/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import java.util.ArrayList
import java.util.Date
import AndroidUtils._
import TextUtils._

abstract class AbstractIssueListActivity[T <: AnyRef] extends Activity with TypedActivity
{
  protected val mTag: String
  
  protected var mIssueListTextView: TextView = null
  private var mItems: ArrayList[T] = null
  private var mIssueListView: ListView = null
  private var mIssueListAdapter: AbstractIssueListActivity.IssueListAdapter[T] = null
  private var mCanLoad = true
  protected var mHandler: Handler = null
  protected var mStopFlag: StopFlag = null
  protected var mReposTimestampInfos: Map[Repository, RepositoryTimestampInfo] = Map()
  protected var mCanShowReposes = true
  protected var mSortingByCreated = false;
  protected val mPerPage = 20
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    log(mTag, "onCreated(): creating ...")
    setContentView(R.layout.issue_list)
    mHandler = new Handler()
    mItems = new ArrayList[T]()
    mIssueListTextView = findView(TR.issueListTextView)
    if(!initialize()) return
    mIssueListView = findView(TR.issueListView)
    mIssueListAdapter = new AbstractIssueListActivity.IssueListAdapter[T](this, mItems, mReposTimestampInfos, mSortingByCreated, mCanShowReposes)(mRepositoryFromItem, mIssueInfoFromItem)
    mIssueListView.setAdapter(mIssueListAdapter)
    mIssueListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long)
      {
        val intent = new Intent(AbstractIssueListActivity.this, classOf[IssueActivity])
        val item = mItems.get(position)
        intent.putExtra(IssueActivity.ExtraRepos, mRepositoryFromItem(item).toJSONObject.toString)
        intent.putExtra(IssueActivity.ExtraIssueInfo, mIssueInfoFromItem(item).toJSONObject.toString)
        AbstractIssueListActivity.this.startActivity(intent)
      }
    })
    mStopFlag = StopFlag(false)
    mIssueListView.setOnScrollListener(new AbsListView.OnScrollListener() {
      override def onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int)
      {
        if(mCanLoad && firstVisibleItem + visibleItemCount > mItems.size()) loadItemsAndUdateListView()
      }
      
      override def onScrollStateChanged(view: AbsListView, scrollState: Int)
      {
      }
    })
    loadItemsAndUdateListView()
  }
  
  override def onDestroy()
  {
    mStopFlag.b = true
    mHandler.removeCallbacksAndMessages(null)
    log(mTag, "onDestroy(): destroyed")
    super.onDestroy()
  }
  
  private def loadItemsAndUdateListView()
  {
    mCanLoad = false
    loadItems {
      (loadedItems, areUnloadedItems) =>
        for(item <- loadedItems) mItems.add(item)
        mIssueListAdapter.unloadedItems = areUnloadedItems
        mIssueListAdapter.notifyDataSetChanged()
        if(areUnloadedItems && mIssueListView.getLastVisiblePosition() + 1 >= mItems.size())
          loadItemsAndUdateListView()
        else
          mCanLoad = true
    } 
  }
  
  protected val mRepositoryFromItem: T => Repository
  
  protected val mIssueInfoFromItem: T => IssueInfo
  
  protected def initialize() = true
  
  protected def loadItems(f: (Vector[T], Boolean) => Unit): Unit
}

object AbstractIssueListActivity
{
  private object LoadingObject
  
  private class IssueListAdapter[T <: AnyRef](activity: Activity, items: ArrayList[T], reposTimestampInfos: Map[Repository, RepositoryTimestampInfo], isSortingByCreated: Boolean, canShowReposes: Boolean)(f: T => Repository, g: T => IssueInfo) extends BaseAdapter
  {
    var unloadedItems = true
    var currentDate: Date = null
    
    def areUnloadItems = unloadedItems
    
    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      var view = convertView
      val listView = parent.asInstanceOf[ListView]
      if(convertView == null) {
        view = activity.getLayoutInflater().inflate(R.layout.issue_item, null)
        val layout = view.findViewById(R.id.issueItemLoadedLayout).asInstanceOf[LinearLayout]
        val openImageView = view.findViewById(R.id.issueItemOpenImageView).asInstanceOf[ImageView]
        val closedImageView = view.findViewById(R.id.issueItemClosedImageView).asInstanceOf[ImageView]
        val reposTextView = view.findViewById(R.id.issueItemReposTextView).asInstanceOf[TextView]
        val titleTextView = view.findViewById(R.id.issueItemTitleTextView).asInstanceOf[TextView]
        val userTextView = view.findViewById(R.id.issueItemUserTextView).asInstanceOf[TextView]
        val dateTextView = view.findViewById(R.id.issueItemDateTextView).asInstanceOf[TextView]
        val progressBar = view.findViewById(R.id.issueItemProgressBar).asInstanceOf[ProgressBar]
        val viewHolder = IssueListAdapter.ViewHolder(position, layout, openImageView, closedImageView, reposTextView, titleTextView, userTextView, dateTextView, progressBar)
        view.setTag(viewHolder)
    	view.setOnClickListener(new View.OnClickListener() {
          override def onClick(view: View)
          {
            if(position < items.size()) listView.performItemClick(convertView, viewHolder.position, getItemId(position)) 
          }
        })
      }
      val viewHolder = view.getTag().asInstanceOf[IssueListAdapter.ViewHolder]
      if(position < items.size()) {
        viewHolder.position = position
        val item = items.get(position)
        val issueInfo = g(item)
        val repos = f(item)
        val isEarlier = reposTimestampInfos.get(repos).map {
          reposTimestampInfo => issueInfo.isEarlierIssue(reposTimestampInfo, isSortingByCreated)
        }.getOrElse(true)
        issueInfo.state match {
          case State.Open   =>
            viewHolder.openImageView.setVisibility(View.VISIBLE)
            viewHolder.closedImageView.setVisibility(View.GONE)
          case State.Closed =>
            viewHolder.openImageView.setVisibility(View.GONE)
            viewHolder.closedImageView.setVisibility(View.VISIBLE)
        }
        if(canShowReposes) {
          viewHolder.reposTextView.setVisibility(View.VISIBLE)
    	  viewHolder.reposTextView.setText(textFromRepository(repos))
        } else
          viewHolder.reposTextView.setVisibility(View.GONE)
        if(isEarlier)
          viewHolder.titleTextView.setTypeface(Typeface.DEFAULT_BOLD)
        else
          viewHolder.titleTextView.setTypeface(Typeface.DEFAULT)
    	viewHolder.titleTextView.setText("#" + issueInfo.number + " " +issueInfo.title)
    	viewHolder.userTextView.setText(issueInfo.user.name)
    	viewHolder.dateTextView.setText(textFromDate(issueInfo.createdAt, currentDate))
    	viewHolder.layout.setVisibility(View.VISIBLE)
    	viewHolder.progessBar.setVisibility(View.GONE)
      } else {
    	viewHolder.layout.setVisibility(View.GONE)
    	viewHolder.progessBar.setVisibility(View.VISIBLE)
      }
      view
    }
    
    override def getItemId(position: Int) = 0
    
    override def getItem(position: Int) = if(position < items.size()) items.get(position) else LoadingObject
    
    override def getCount() = items.size() + (if(areUnloadItems) 1 else 0)
    
    override def notifyDataSetChanged()
    {
      currentDate = new Date()
      super.notifyDataSetChanged()
    }
  }
  
  private object IssueListAdapter
  {
    private case class ViewHolder(
        var position: Int, 
        layout: LinearLayout, 
        openImageView: ImageView,
        closedImageView: ImageView,
        reposTextView: TextView,
        titleTextView: TextView,
        userTextView: TextView,
        dateTextView: TextView,
        progessBar: ProgressBar)
  }
}
