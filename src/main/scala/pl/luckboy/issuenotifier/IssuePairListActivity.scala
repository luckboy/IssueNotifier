/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import scala.annotation.tailrec
import scala.collection.mutable.PriorityQueue
import scala.collection.mutable.{ Map => MutableMap }
import AndroidUtils._
import DataStorage._
import LogStringUtils._

class IssuePairListActivity extends AbstractIssueListActivity[IssuePair]
{
  override protected val mTag = getClass().getSimpleName()
  
  private var mIssueListTextView: TextView = null
  private var mReposes: Vector[Repository] = null
  private var mState: RequestIssueState = null
  private var mSortingByCreated = false
  private var mSorting: IssueSorting.Value = null
  private var mPage = 1L
  private var mIssuePairQueue: PriorityQueue[IssuePair] = null
  private var mUnloadedIssuePairReposes: Set[Repository] = null
  private var mReposIssueCounts: Map[Repository, Long] = null
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    log(mTag, "onCreated(): created")
  }

  override def onDestroy()
  {
    log(mTag, "onDestroy(): destroying ...")
    super.onDestroy()
  }
    
  override protected val mRepositoryFromItem = (issuePair: IssuePair) => issuePair.repos
  
  override protected val mIssueInfoFromItem = (issuePair: IssuePair) => issuePair.issueInfo
  
  override protected def initialize() = {
    mIssueListTextView = findView(TR.issueListTextView)
    mIssueListTextView.setText(getResources().getString(R.string.issue_list_issues_from_all_reposes))
    loadRepositories(this) match {
      case Left(e)        => 
        false
      case Right(reposes) =>
        mReposes = reposes
        val settings = Settings(this)
        mState = settings.state
        mSortingByCreated = settings.sortingByCreated
        mSorting = if(settings.sortingByCreated) IssueSorting.Created else IssueSorting.Updated
        mPage = 1
        val issuePairOrdering = new Ordering[IssuePair] {
          override def compare(issuePair1: IssuePair, issuePair2: IssuePair) =
            if(!mSortingByCreated)
              issuePair1.issueInfo.updatedAt.compareTo(issuePair2.issueInfo.updatedAt)
            else
              issuePair1.issueInfo.createdAt.compareTo(issuePair2.issueInfo.createdAt)
        }
        mIssuePairQueue = PriorityQueue()(issuePairOrdering)
        mUnloadedIssuePairReposes = mReposes.toSet
        mReposIssueCounts = mReposes.map { _ -> 0L }.toMap
        true
    }
  }
  
  override protected def loadItems(f: (Vector[IssuePair], Boolean) => Unit)
  {
    val tmpReposes = mReposes
    val tmpState = mState
    val tmpSorting = mSorting
    val tmpPage = mPage
    val tmpIssuePairQueue = mIssuePairQueue
    val tmpUnloadedIssuePairReposes = mUnloadedIssuePairReposes
    val tmpReposIssueCounts = mReposIssueCounts
    log(mTag, "loadItems(): tmpState = " + tmpState)
    log(mTag, "loadItems(): tmpSorting = " + tmpSorting)
    log(mTag, "loadItems(): tmpPage = " + tmpPage)
    for(p <- tmpReposes.zipWithIndex)
      log(mTag, "loadItems(): tmpReposes(" + p._2 + ") = " + stringFromRepository(p._1))
    log(mTag, "loadItems(): tmpIssuePairQueue.size = " + tmpIssuePairQueue.size)
    for(p <- tmpUnloadedIssuePairReposes.zipWithIndex)
      log(mTag, "loadItems(): tmpUnloadedIssuePairReposes(" + p._2 + ") = " + stringFromRepository(p._1))
    for(p <- tmpReposIssueCounts)
      log(mTag, "loadItems(): tmpReposIssueCounts(" + stringFromRepository(p._1) + ") = " + p._2)
    startThreadAndPost(mHandler, mStopFlag) {
      () =>
        (Vector() ++ tmpUnloadedIssuePairReposes).foldLeft(Right(Vector()): Either[Exception, Vector[(Repository, Vector[IssueInfo])]]) {
          case (left @ Left(_), _)            => left
          case (Right(issueInfoLists), repos) =>
            if(tmpReposIssueCounts.getOrElse(repos, 0L) <= 0L)
              MainService.DataFetchers.get(repos.server).map {
                dataFetcher =>
                  log(mTag, "loadItems(): fetching issues from " + stringFromRepository(repos) + " ...")
                  val res = log(mTag, dataFetcher.fetchIssueInfos(
                      repos, Some(tmpState), Some(tmpSorting), Some(Direction.Desc), None, 
                      Some(tmpPage), Some(mPerPage), Some(30000)))
                  log(mTag, "loadItems(): fetched issues from " + stringFromRepository(repos) +
                      res.fold(_ => "", issueInfos => " (issueInfoCount = " + issueInfos.size + ")"))
                  res
              }.getOrElse(Right(Vector())) match {
                case Left(e) => Left(e)
                case Right(issueInfos) => Right(issueInfoLists :+ (repos -> issueInfos))
              }
            else
              Right(Vector())
        } match {
          case Left(e)               => Left(e)
          case Right(issueInfoLists) =>
            tmpIssuePairQueue ++= issueInfoLists.flatMap {
              case (repos, issueInfos) => issueInfos.map { issueInfo => IssuePair(repos, issueInfo) }
            }
            val unloadedIssuePairReposes = issueInfoLists.flatMap {
              case (repos, issueInfos) => if(issueInfos.size >= mPerPage) Vector(repos) else Vector()
            }.toSet
            val tmpReposIssueCounts2 = issueInfoLists.map { 
              case (repos, issueInfos) => repos -> issueInfos.size
            }.toMap
            val tmpReposIssueCounts3 = tmpReposIssueCounts.map {
              case (repos, issueCount) => repos -> (issueCount + tmpReposIssueCounts2.getOrElse(repos, 0))
            }
            dequeueIssuePairQueue(tmpIssuePairQueue, unloadedIssuePairReposes, Vector(), MutableMap() ++ tmpReposIssueCounts3) match {
              case (issuePairs, issuePairQueue, reposIssueCounts) =>
                val areUnloadedItems = !unloadedIssuePairReposes.isEmpty
                Right((issuePairs, issuePairQueue, unloadedIssuePairReposes, reposIssueCounts.toMap, areUnloadedItems))
            }
        }
    } {
      case Left(_)      =>
        showDialog(IssuePairListActivity.DialogFetchingError)
        f(Vector(), false)
      case Right(tuple) =>
        val (issuePairs, issuePairQueue, unloadedIssuePairReposes, reposIssueCounts, areUnloadedItems) = tuple
        log(mTag, "loadItems(): issuePairs.size = " + issuePairs.size)
        log(mTag, "loadItems(): issuePairQueue.size = " + issuePairQueue.size)
        for(p <- unloadedIssuePairReposes.zipWithIndex)
          log(mTag, "loadItems(): unloadedIssuePairReposes(" + p._2 + ") = " + stringFromRepository(p._1))
        for(p <- reposIssueCounts)
          log(mTag, "loadItems(): reposIssueCounts(" + stringFromRepository(p._1) + ") = " + p._2)
        log(mTag, "loadItems(): areUnloadedItems = " + areUnloadedItems)
        mPage += 1
        f(issuePairs, areUnloadedItems)
    }
  }
  
  @tailrec
  private def dequeueIssuePairQueue(queue: PriorityQueue[IssuePair], unloadedIssuePairReposes: Set[Repository], issuePairs: Vector[IssuePair], reposIssueCounts: MutableMap[Repository, Long]): (Vector[IssuePair], PriorityQueue[IssuePair], MutableMap[Repository, Long]) =
    if(queue.isEmpty) {
      (issuePairs, queue, reposIssueCounts)
    } else {
      val issuePair = queue.dequeue()
      val issueCount = reposIssueCounts.getOrElse(issuePair.repos, 0L) - 1L
      reposIssueCounts += (issuePair.repos -> issueCount)
      val newIssuePairs = issuePairs :+ issuePair
      if(issueCount > 0 || !unloadedIssuePairReposes.contains(issuePair.repos))
        dequeueIssuePairQueue(queue, unloadedIssuePairReposes, newIssuePairs, reposIssueCounts)
      else
        (newIssuePairs, queue, reposIssueCounts)
    }

  override def onCreateDialog(id: Int, bundle: Bundle) =
    id match {
      case IssuePairListActivity.DialogFetchingError =>
        createErrorDialog(this, getResources().getString(R.string.fetching_error_message))
      case IssuePairListActivity.DialogIOError       =>
        createErrorDialog(this, getResources().getString(R.string.io_error_message))
      case _                                         =>
        super.onCreateDialog(id, bundle)
    }
}

object IssuePairListActivity
{
  private val DialogFetchingError = 0
  private val DialogIOError = 1
}
