/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.Activity
import android.os.Bundle
import org.json.JSONObject
import AndroidUtils._
import LogStringUtils._

class IssueListActivity extends AbstractIssueListActivity[IssueInfo]
{
  override protected val mTag = getClass().getSimpleName()
  
  private var mRepos: Repository = null
  private var mState: RequestIssueState = null
  private var mSorting: IssueSorting.Value = null
  private var mPage = 1L

  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    log(mTag, "onCreated(): created")
  }
  
  override def onDestroy()
  {
    log(mTag, "onCreated(): destroying ...")
    super.onDestroy()
  }
  
  override protected val mRepositoryFromItem = (issueInfo: IssueInfo) => mRepos
  
  override protected val mIssueInfoFromItem = (issueInfo: IssueInfo) => issueInfo
  
  override protected def initialize() =
    try {
      Repository.fromJSONObject(new JSONObject(getIntent().getStringExtra(IssueListActivity.ExtraRepos))) match {
        case Left(e)      => false
        case Right(repos) =>
          mRepos = repos
          val settings = Settings(this)
          mState = settings.state
          mSorting = if(settings.sortingByCreated) IssueSorting.Created else IssueSorting.Updated
          mPage = 1
          true
      }
    } catch {
      case e: Exception => false
    }
  
  override protected def loadItems(f: (Vector[IssueInfo], Boolean) => Unit)
  {
    val tmpRepos = mRepos
    val tmpState = mState
    val tmpSorting = mSorting
    val tmpPage = mPage
    log(mTag, "loadItems(): tmpState = " + tmpState)
    log(mTag, "loadItems(): tmpSorting = " + tmpSorting)
    log(mTag, "loadItems(): tmpPage = " + tmpPage)
    log(mTag, "loadItems(): tmpRepos = " + stringFromRepository(tmpRepos))
    startThreadAndPost(mHandler, mStopFlag) {
      () =>
        val res = MainService.DataFetchers.get(tmpRepos.server).map {
          dataFetcher => 
            log(mTag, "loadItems(): fetching issues from " + stringFromRepository(tmpRepos) + " ...")
            val res = log(mTag, dataFetcher.fetchIssueInfos(
                tmpRepos, Some(tmpState), Some(tmpSorting), Some(Direction.Desc), None,
                Some(tmpPage), Some(mPerPage), Some(30000)))
            log(mTag, "loadItems(): fetched issues from " + stringFromRepository(tmpRepos) +
                res.fold(_ => "", issueInfo => " (issueInfoCount = " + issueInfo.size + ")"))
            res
        }.getOrElse(Right(Vector()))
        res match {
          case Left(e)           => Vector()
          case Right(issueInfos) => issueInfos
        }
    } {
      issueInfos =>
        mPage += 1
        f(issueInfos, issueInfos.size >= mPerPage)
    }
  }
}

object IssueListActivity
{
  val ExtraRepos = classOf[MainReceiver].getName() + ".ExtraRepos"
}
