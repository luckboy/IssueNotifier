/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.webkit.WebView
import java.util.Date
import org.json.JSONObject
import AndroidUtils._
import DataStorage._
import LogStringUtils._
import TextUtils._

class IssueActivity extends Activity with TypedActivity
{
  private val mTag = getClass().getSimpleName()
  
  private var mRepos: Repository = null
  private var mIssueInfo: IssueInfo = null
  private var mIssueWebView: WebView = null
  private var mCommentListHtml: IssueActivity.CommentListHtml = null
  private var mUnloadedCommentListFlag: IssueActivity.UnloadedCommentListFlag = null
  private var mCanLoad = true
  private var mHandler: Handler = null
  private var mStopFlag: StopFlag = null
  private var mPage = 1L
  private val mPerPage = 20
  private var mCurrentDate: Date = null
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    setContentView(R.layout.issue)
    log(mTag, Repository.fromJSONObject(new JSONObject(getIntent().getStringExtra(IssueActivity.ExtraRepos)))) match {
      case Left(_)      => return
      case Right(repos) => mRepos = repos
    }
    log(mTag, IssueInfo.fromJSONObject(new JSONObject(getIntent().getStringExtra(IssueActivity.ExtraIssueInfo)))) match {
      case Left(_)          => return
      case Right(issueInfo) => mIssueInfo = issueInfo
    }
    mIssueWebView = findView(TR.issueWebView)
    mIssueWebView.getSettings().setJavaScriptEnabled(true)
    val f = {
      () => 
        mHandler.post(new Runnable() {
          override def run()
          {
            loadComments()
          }
        })
        ()
    }
    mCommentListHtml = new IssueActivity.CommentListHtml("")
    mUnloadedCommentListFlag = new IssueActivity.UnloadedCommentListFlag(true)
    mIssueWebView.addJavascriptInterface(new IssueActivity.JSObject(f, mCommentListHtml, mUnloadedCommentListFlag), "IssueNotifierObject")
    val s = (
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n" + 
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
        "<head>\n" +
        "<link rel=\"stylesheet\" href=\"file:///android_asset/issue.css\"/>\n" +
        "<script src=\"file:///android_asset/loading.js\" type=\"text/javascript\"></script>\n" +
        "</head>\n" +
        "<body onload=\"startLoadingAnimation()\">\n" +
        "<div id=\"loading\"></div>" +
        "</body>\n" +
        "</html>")
    mIssueWebView.loadDataWithBaseURL("file:///android_res/", s, "text/html", "UTF-8", "")
	mHandler = new Handler()
    startLoadingAnimation()
    mStopFlag = StopFlag(false)
    mCanLoad = false
    val tmpRepos = mRepos
    val tmpIssueInfo = mIssueInfo
    startThreadAndPost(mHandler, mStopFlag) {
      () =>
        MainService.DataFetchers.get(tmpRepos.server).map {
          dataFetcher =>
            log(mTag, "onCreate(): fetching issue from " + stringFromRepository(tmpRepos) + " ...")
            val res = log(mTag, dataFetcher.fetchIssue(tmpRepos, tmpIssueInfo, Some(30000)))
            log(mTag, "onCreate(): fetched issue from " + stringFromRepository(tmpRepos))
            res.fold(e => Left(e), issue => Right(Some(issue)))
        }.getOrElse(Right(None))
    } {
      case Left(_)            =>
        showDialog(IssueActivity.DialogFetchingError)
      case Right(Some(issue)) =>
        stopLoadingAnimation()
        mCurrentDate = new Date()
        val s2 = (
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n" + 
            "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
            "<head>\n" +
            "<link rel=\"stylesheet\" href=\"file:///android_asset/issue.css\"/>\n" +
            "<script src=\"file:///android_asset/loading.js\" type=\"text/javascript\"></script>\n" +
            "<script src=\"file:///android_asset/issue.js\" type=\"text/javascript\"></script>\n" +
            "</head>\n" +
            "<body onscroll=\"onScroll()\">\n" +
            htmlFromIssue(issue) + "\n" +
            "<div id=\"comments\"></div>\n" +
            "<div id=\"loading\"></div>" +
            "</body>\n" +
            "</html>")
        mIssueWebView.loadDataWithBaseURL("file:///android_res/", s2, "text/html", "UTF-8", "")
        mCanLoad = true
        loadComments()
        startLoadingAnimation()
      case Right(None)        =>
        stopLoadingAnimation()
        startLoadingAnimation()
    }
  }
  
  override def onDestroy()
  {
    mStopFlag.b = true
    stopLoadingAnimation()
    super.onDestroy()
  }

  private def htmlFromIssue(issue: Issue) = {
    val stateStyleClassName = issue.info.state match {
      case State.Open   => "block-state-icon-open"
      case State.Closed => "block-state-icon-closed"
    }
    "<div id=\"issue\">\n" +
    "\t<div class=\"header\">\n" +
    "\t\t<div class=\"block-repos block-repos-icon\">\n" +
    "\t\t\t<div class=\"repos\">" + htmlFromString(textFromRepository(mRepos)) + "</div>\n" +
    "\t\t</div>\n" +
    "\t\t<div class=\"block-state-and-title " + stateStyleClassName + "\">\n" +
    "\t\t\t<div class=\"title\">" + htmlFromString("#" + issue.info.number + " " + issue.info.title) + "</div>\n" +
    "\t\t</div>\n" +
    "\t\t<div class=\"block-user-and-date\">\n" +
    "\t\t\t<span class=\"user\">" + htmlFromString(issue.info.user.name) + "</span>\n" +
    "\t\t\t<span class=\"date\">" + htmlFromString(textFromDate(issue.info.createdAt, mCurrentDate)) + "</span>\n" +
    "\t\t\t<div style=\"clear: both\"></div>\n" +
    "\t\t</div>\n" +
    "\t</div>\n" +
    "\t<div class=\"body\">" + issue.bodyHtml + "</div>\n" +
    "</div>"
  }
  
  private def htmlFromComment(comment: Comment) =
    "<div class=\"comment\">\n" +
    "\t<div class=\"header\">\n" +
    "\t\t<div class=\"block-user-and-date\">\n" +
    "\t\t\t<span class=\"user\">" + htmlFromString(comment.user.name) + "</span>\n" +
    "\t\t\t<span class=\"date\">" + htmlFromString(textFromDate(comment.createdAt, mCurrentDate)) + "</span>\n" +
    "\t\t\t<div style=\"clear: both\"></div>\n" +
    "\t\t</div>\n" +
    "\t</div>\n" +
    "\t<div class=\"body\">" + comment.bodyHtml + "</div>\n" +
    "</div>"
  
  private def loadComments()
  {
    if(mCanLoad && mUnloadedCommentListFlag.flag) {
      mCanLoad = false
      val tmpRepos = mRepos
      val tmpIssueInfo = mIssueInfo
      val tmpPage = mPage
      startThreadAndPost(mHandler, mStopFlag) {
        () =>
          MainService.DataFetchers.get(tmpRepos.server).map {
            dataFetcher => 
              log(mTag, "loadComments(): fetching comments from " + stringFromRepositoryAndIssueInfo(tmpRepos, tmpIssueInfo) + " ...")
              val res = log(mTag, dataFetcher.fetchComments(
                  tmpRepos, tmpIssueInfo, Some(CommentSorting.Created), Some(Direction.Asc), None,
                  Some(tmpPage), Some(mPerPage), Some(30000)))
              log(mTag, "loadComments(): fetched comments from " +  stringFromRepositoryAndIssueInfo(tmpRepos, tmpIssueInfo) +
                  res.fold(_ => "", comments => " (commentCount = " + comments.size + ")"))
              res
          }.getOrElse(Right(Vector())) match {
            case Left(e)         => Left(e)
            case Right(comments) => 
              Right(comments.map(htmlFromComment).mkString("\n"), comments.size >= mPerPage)
          }
      } {
        case Left(_)                            =>
          showDialog(IssueActivity.DialogFetchingError)
          mCommentListHtml.html = ""
          mUnloadedCommentListFlag.flag = false
          mCanLoad = true
          mIssueWebView.loadUrl("javascript:onLoadComments()")
        case Right((html, areUnloadedComments)) =>
          mPage += 1
          mCommentListHtml.html = html
          mUnloadedCommentListFlag.flag = areUnloadedComments
          mCanLoad = true
          mIssueWebView.loadUrl("javascript:onLoadComments()")
      }
    }
  }
  
  private val mLoadingDrawingRunnable: Runnable = new Runnable() {
    override def run()
    {
      val startTime = System.currentTimeMillis()
      mIssueWebView.loadUrl("javascript:onDrawLoading()")
      val timeDiff = System.currentTimeMillis() - startTime;
      val interval = 20
      if(interval - timeDiff > 0)
        mHandler.postDelayed(mLoadingDrawingRunnable, interval - timeDiff)
      else
        mHandler.post(mLoadingDrawingRunnable)
    }
  }
  
  def startLoadingAnimation()
  {
    mHandler.post(mLoadingDrawingRunnable)
  }
  
  def stopLoadingAnimation()
  {
    mHandler.removeCallbacks(mLoadingDrawingRunnable)
  }

  override def onCreateDialog(id: Int, bundle: Bundle) =
    id match {
      case IssueActivity.DialogFetchingError =>
        createErrorDialog(this, getResources().getString(R.string.fetching_error_message))
      case _                                 =>
        super.onCreateDialog(id, bundle)
    }
}

object IssueActivity
{
  private val DialogFetchingError = 0
  
  val ExtraRepos = classOf[IssueActivity].getName() + ".Repos"
  val ExtraIssueInfo = classOf[IssueActivity].getName() + ".ExtraIssueInfo"
  
  private class CommentListHtml(private var s: String)
  {
    def html = synchronized(s)
    
    def html_=(s2: String) = synchronized { s = s2 }
  }
  
  private class UnloadedCommentListFlag(private var b: Boolean)
  {
    def flag = synchronized(b)
    
    def flag_=(b2: Boolean) = synchronized { b = b2 }    
  }
  
  private class JSObject(f: () => Unit, commentListHtml: CommentListHtml, unloadedCommentListFlag: UnloadedCommentListFlag)
  {
    def loadComments(): Unit = f()
    
    def getCommentListHtml(): String = commentListHtml.html
    
    def areUnloadedComments(): Boolean = unloadedCommentListFlag.flag
    
    def log(s: String): Unit = AndroidUtils.log("IssueActivity.JSObject", s)
  }
}
