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
import java.text.SimpleDateFormat
import org.json.JSONObject
import AndroidUtils._
import DataStorage._
import LogStringUtils._

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
    mHandler = new Handler()
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
        }.getOrElse(Right(None)) match {
          case Left(_)      => None
          case Right(optIssue) => optIssue 
        }
    } {
      case Some(issue) =>
        val stateStr = issue.info.state match {
          case State.Open   => "!"
          case State.Closed => "\u2713"
        }
        val s = (
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "<link rel=\"stylesheet\" href=\"file:///android_asset/issue.css\">\n" +
            "<script src=\"file:///android_asset/issue.js\" type=\"text/javascript\"></script>\n" +
            "</head>\n" +
            "<body onscroll=\"onScroll()\">\n" +
            htmlFromIssue(issue) + "\n" +
            "<div id=\"comments\"></div>\n" +
            "<div id=\"loading\">Loading ...</div>" +
            "</body>\n" +
            "</html>")
        mIssueWebView.loadDataWithBaseURL("file:///android_asset/", s, "text/html", "UTF-8", "")
        mCanLoad = true
        loadComments()
      case None        => ()
    }
  }
  
  override def onDestroy()
  {
    mStopFlag.b = true
    super.onDestroy()
  }

  private val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  
  private def htmlFromIssue(issue: Issue) =
    "<div id=\"issue\">\n" +
    "\t<div class=\"header\">\n" +
    "\t\t<h1>Issue of " + htmlFromString(mRepos.userName + "/" + mRepos.name) + "</h1>\n" +
    "\t\t<h2>" + htmlFromString(issue.info.title) + "</h2>\n" +
    "\t\t<span class=\"user\">" + htmlFromString(issue.info.user.name) + "</span>\n" +
    "\t\t<span class=\"date\">" + simpleDateFormat.format(issue.info.createdAt) + "</span>\n" +
    "\t</div>\n" +
    "\t<div class=\"body\">" + issue.bodyHtml + "</div>\n" +
    "</div>"
  
  private def htmlFromComment(comment: Comment) =
    "<div class=\"comment\">\n" +
    "\t<div class=\"header\">\n" +
    "\t\t<span class=\"user\">" + htmlFromString(comment.user.name) + "</span>\n" +
    "\t\t<span class=\"date\">" + simpleDateFormat.format(comment.createdAt) + "</span>\n" +
    "\t</div>" +
    "\t<div class=\"body\">" + comment.bodyHtml + "</div>" +
    "</div>"
  
  private def loadComments()
  {
    if(mCanLoad) {
      log(mTag, "loadComments(): loading ...")
      mCanLoad = false
      val tmpRepos = mRepos
      val tmpIssueInfo = mIssueInfo
      val tmpPage = mPage
      startThreadAndPost(mHandler, mStopFlag) {
        () =>
          val comments = MainService.DataFetchers.get(tmpRepos.server).map {
            dataFetcher => 
              log(mTag, "loadComments(): fetching comments from " + stringFromRepositoryAndIssueInfo(tmpRepos, tmpIssueInfo) + " ...")
              val res = log(mTag, dataFetcher.fetchComments(
                  tmpRepos, tmpIssueInfo, Some(CommentSorting.Created), Some(Direction.Asc), None,
                  Some(tmpPage), Some(mPerPage), Some(30000)))
              log(mTag, "loadComments(): fetched comments from " +  stringFromRepositoryAndIssueInfo(tmpRepos, tmpIssueInfo) +
                  res.fold(_ => "", comments => " (commentCount = " + comments.size + ")"))
              res
          }.getOrElse(Right(Vector())) match {
            case Left(e)         => Vector()
            case Right(comments) => comments
          }
          (comments.map(htmlFromComment).mkString("\n"), comments.size >= mPerPage)
      } {
        case (html, areUnloadedComments) =>
          mPage += 1
          mCommentListHtml.html = html
          mUnloadedCommentListFlag.flag = areUnloadedComments
          mCanLoad = true
          mIssueWebView.loadUrl("javascript:onLoadComments()")
          log(mTag, "loadComments(): loaded")
      }
    }
  }
}

object IssueActivity
{
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
    
    //def log(s: String): Unit = AndroidUtils.log("IssueActivity.JSObject", s)
  }
}
