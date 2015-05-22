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
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.ArrayList
import AndroidUtils._

abstract class AbstractIssueListActivity[T <: AnyRef] extends Activity with TypedActivity
{
  protected val mTag: String
  
  private var mItems: ArrayList[T] = null
  private var mIssueListView: ListView = null
  private var mIssueListAdapter: AbstractIssueListActivity.IssueListAdapter[T] = null
  private var mCanLoad = true
  protected var mHandler: Handler = null
  protected var mStopFlag: StopFlag = null
  protected val mPerPage = 20
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    setContentView(R.layout.issue_list)
    mHandler = new Handler()
    mItems = new ArrayList[T]()
    if(!initialize()) return
    mIssueListView = findView(TR.issueListView)
    mIssueListAdapter = new AbstractIssueListActivity.IssueListAdapter[T](this, mItems)(mRepositoryFromItem, mIssueInfoFromItem)
    mIssueListView.setAdapter(mIssueListAdapter)
    mIssueListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long)
      {
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
        if(mIssueListView.getLastVisiblePosition() + 1 >= mItems.size())
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
  private val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  
  private object LoadingObject
  
  private class IssueListAdapter[T <: AnyRef](activity: Activity, items: ArrayList[T])(f: T => Repository, g: T => IssueInfo) extends BaseAdapter
  {
    var unloadedItems = true
    
    def areUnloadItems = unloadedItems
    
    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      var view = convertView
      val listView = parent.asInstanceOf[ListView]
      if(convertView == null) {
        view = activity.getLayoutInflater().inflate(R.layout.issue_item, null)
        val layout = view.findViewById(R.id.issueItemLoadedLayout).asInstanceOf[LinearLayout]
        val stateTextView = view.findViewById(R.id.issueItemStateTextView).asInstanceOf[TextView]
        val numberTextView = view.findViewById(R.id.issueItemNumberTextView).asInstanceOf[TextView]
        val titleTextView = view.findViewById(R.id.issueItemTitleTextView).asInstanceOf[TextView]
        val reposTextView = view.findViewById(R.id.issueItemReposTextView).asInstanceOf[TextView]
        val dateTextView = view.findViewById(R.id.issueItemDateTextView).asInstanceOf[TextView]
        val progressBar = view.findViewById(R.id.issueItemProgressBar).asInstanceOf[ProgressBar]
        view.setTag(IssueListAdapter.ViewHolder(layout, stateTextView, numberTextView, titleTextView, reposTextView, dateTextView, progressBar))
      }
      val viewHolder = view.getTag().asInstanceOf[IssueListAdapter.ViewHolder]
      if(position < items.size()) {
        val item = items.get(position)
        val issueInfo = g(item)
        val stateStr = issueInfo match {
          case State.Open   => "!"
          case State.Closed => "\u2713"
          case State.All    => ""
        }
    	viewHolder.stateTextView.setText(stateStr)
    	viewHolder.numberTextView.setText("#" + issueInfo.number)
    	viewHolder.titleTextView.setText(issueInfo.title)
    	val repos = f(item)
    	viewHolder.reposTextView.setText(repos.userName + "/" + repos.name)
    	viewHolder.dateTextView.setText(simpleDateFormat.format(issueInfo.createdAt))
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
  }
  
  private object IssueListAdapter
  {
    private case class ViewHolder(
        layout: LinearLayout, 
        stateTextView: TextView,
        numberTextView: TextView,
        titleTextView: TextView,
        reposTextView: TextView,
        dateTextView: TextView,
        progessBar: ProgressBar)
  }
}
