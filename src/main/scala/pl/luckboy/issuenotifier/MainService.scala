/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.Binder
import java.util.Date
import AndroidUtils._
import DataStorage._
import android.content.IntentFilter

class MainService extends Service
{
  private val mTag = getClass().getSimpleName()
  
  private var mHandler: Handler = null
  private var mReposes: Vector[Repository] = null
  private var mLastReposTimestampInfos: Map[Repository, RepositoryTimestampInfo] = null
  private var mOldReposTimestampInfos: Map[Repository, RepositoryTimestampInfo] = null
  private var mStopFlag: StopFlag = null
  private var mSettings: Settings = null
  
  private val mBinder = new Binder {
    def getService(): MainService = MainService.this
  }
  
  override def onBind(ident: Intent) = mBinder
  
  override def onCreate()
  {
    mHandler = new Handler()
  }
  
  private def fetchAndNotify(stopFlag: StopFlag)()
  {
    val tmpReposes = mReposes
    val tmpLastReposTimestampInfos = mLastReposTimestampInfos
    val tmpOldReposTimestampInfos = mOldReposTimestampInfos
    val tmpInterval = mSettings.interval
    val tmpState = mSettings.state
    val tmpSortingByCreated = mSettings.sortingByCreated
    val tmpSorting = if(mSettings.sortingByCreated) IssueSorting.Created else IssueSorting.Updated
    log(mTag, "fetchAndNotify(): tmpInverval = " + tmpInterval)
    log(mTag, "fetchAndNotify(): tmpState = " + tmpState)
    log(mTag, "fetchAndNotify(): tmpSortingByCreated = " + tmpSortingByCreated)
    log(mTag, "fetchAndNotify(): tmpSorting = " + tmpSorting)
    for(p <- mLastReposTimestampInfos)
      log(mTag, "fetchAndNotify(): LRTIs(" + stringFromRepository(p._1) + ") = " + stringFromRepositoryTimestampInfo(p._2))
    for(p <- mOldReposTimestampInfos)
      log(mTag, "fetchAndNotify(): ORTIs(" + stringFromRepository(p._1) + ") = " + stringFromRepositoryTimestampInfo(p._2))
    startThreadAndPost(mHandler, stopFlag) {
      () =>
        val issueInfoLists = tmpReposes.map {
          repos =>
            val since = tmpOldReposTimestampInfos.get(repos).map {
              reposTimestampInfo =>
                if(!tmpSortingByCreated) reposTimestampInfo.updatedIssueAt else reposTimestampInfo.createdIssueAt
            }
            MainService.DataFetchers.get(repos.server).map {
              dataFetcher =>
                log(mTag, "fetchAndNotify(): fetching issues from " + stringFromRepository(repos) + " ...")
                log(mTag, "fetchAndNotify(): since = " + since)
                val res = dataFetcher.fetchIssueInfos(
                    repos, Some(tmpState), Some(tmpSorting), Some(Direction.Desc), since, 
                    Some(1), Some(2), Some(5000))
                log(mTag, "fetchAndNotify(): fetched issues from " + stringFromRepository(repos) + res.fold(_ => "", issueInfo => " (issueInfoCount = " + issueInfo.size + ")"))
                res
            }.getOrElse(Right(Vector())) match {
              case left @ Left(_)    => log(mTag, left); repos -> Vector()
              case Right(issueInfos) =>
                val newIssueInfos = since.map {
                  date =>
                    issueInfos.filter {
                      issueInfo =>
                        if(!tmpSortingByCreated)
                          issueInfo.updatedAt.compareTo(date) > 0
                        else
                          issueInfo.createdAt.compareTo(date) > 0
                    }
                }.getOrElse(issueInfos)
                repos -> newIssueInfos
            }
        }
        val reposTimestampInfos = issueInfoLists.map {
          case (repos, issueInfos) => 
            val tmpCreatedIssueAt = tmpLastReposTimestampInfos.get(repos).map {
              reposTimestampInfo =>
                if(!tmpSortingByCreated) reposTimestampInfo.updatedIssueAt else reposTimestampInfo.createdIssueAt
            }.getOrElse(new Date(0))
            val createdIssueAt = issueInfos.foldLeft(tmpCreatedIssueAt) {
              case (date, issueInfo) => if(date.compareTo(issueInfo.createdAt) > 0) date else issueInfo.createdAt
            }
            val tmpUpdatedIssueAt = tmpLastReposTimestampInfos.get(repos).map {
              reposTimestampInfo =>
                if(!tmpSortingByCreated) reposTimestampInfo.updatedIssueAt else reposTimestampInfo.createdIssueAt
            }.getOrElse(new Date(0))
            val updatedIssueAt = issueInfos.foldLeft(tmpUpdatedIssueAt) {
              case (date, issueInfo) => if(date.compareTo(issueInfo.updatedAt) > 0) date else issueInfo.updatedAt
            }
            repos -> RepositoryTimestampInfo(createdIssueAt, updatedIssueAt)
        }.toMap
        val mustNotify = issueInfoLists.exists { !_._2.isEmpty }
        val issueCount = issueInfoLists.foldLeft(0) { _ + _._2.size }
        (reposTimestampInfos, mustNotify, issueCount)
    } {
      case (reposTimestampInfos, mustNotify, issueCount) =>
        mLastReposTimestampInfos = reposTimestampInfos
        for(p <- mLastReposTimestampInfos)
          log(mTag, "fetchAndNotify(): newLRTIs(" + stringFromRepository(p._1) + ") = " + stringFromRepositoryTimestampInfo(p._2))
        log(mTag, storeLastRepositoryTimestampInfos(this, mLastReposTimestampInfos))
        if(mustNotify) {
          val title = getResources().getQuantityString(R.plurals.notification_issues_title, issueCount)
          val message = getResources().getQuantityString(R.plurals.notification_issues_message, issueCount)
          val intent = new Intent(MainReceiver.ActionIssuePairs)
          log(mTag, "fetchAndNotify(): intent.getAction() = " + intent.getAction())
          val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
          log(mTag, "fetchAndNotify(): notify(..., " + title + ", " + message + ", ...)")
          AndroidUtils.notify(this, android.R.drawable.star_on, title, message, Some(pendingIntent), true)
        }
        postDelayed(mHandler, tmpInterval)(fetchAndNotify(stopFlag))
    }
  }
  
  override def onStartCommand(intent: Intent, flags: Int, startId: Int) = {
    log(mTag, "onStartCommand(): starting ...")
    mSettings = Settings(this)
    mReposes = log(mTag, loadRepositories(this)).fold(_ => Vector(), identity)
    mLastReposTimestampInfos = log(mTag, loadLastRepositoryTimestampInfos(this)).fold(_ => Map(), identity)
    mOldReposTimestampInfos = log(mTag, loadOldRepositoryTimestampInfos(this)).fold(_ => Map(), identity)
    mStopFlag = StopFlag(false)
    log(mTag, "onStartCommand(): started")
    post(mHandler)(fetchAndNotify(mStopFlag))
    Service.START_NOT_STICKY
  }
  
  override def onDestroy()
  {
    log(mTag, "onDestroy(): stopping ...")
    mStopFlag.b = true
    mHandler.removeCallbacksAndMessages(null)
    log(mTag, "onDestroy(): stopped")
  }
  
  private def stringFromRepository(repos: Repository) =
    repos.server.name + ":" + repos.userName + "/" + repos.name
  
  private def stringFromRepositoryTimestampInfo(reposTimestampInfo: RepositoryTimestampInfo) =
    "RTI(" + reposTimestampInfo.createdIssueAt + ", " + reposTimestampInfo.updatedIssueAt + ")"
}

object MainService
{
  val DataFetchers = Map[Server, DataFetcher](GitHubServer() -> DataFetcher(GitHubServer()))
}

