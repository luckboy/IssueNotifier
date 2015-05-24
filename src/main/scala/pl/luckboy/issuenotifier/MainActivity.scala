/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.EditText
import android.widget.TextView
import java.util.ArrayList
import java.util.List
import scala.collection.immutable.BitSet
import AndroidUtils._
import DataStorage._

class MainActivity extends Activity with TypedActivity
{
  private val mTag = getClass().getSimpleName()
    
  private var mOptionsMenu: Menu = null
  private var mReposes: ArrayList[Repository] = null
  private var mReposListView: ListView = null
  private var mReposListAdapter: MainActivity.RepositoryListAdapter = null
  private var mSettings: Settings = null
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    setContentView(R.layout.main)
    val reposes = log(mTag, loadRepositories(this)).fold(_ => Vector(), identity)
    mReposes = new ArrayList[Repository]()
    for(repos <- reposes) mReposes.add(repos)
    mReposListView = findView(TR.reposListView)
    mReposListAdapter = new MainActivity.RepositoryListAdapter(this, mReposes)
    mReposListView.setAdapter(mReposListAdapter)
    mReposListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long)
      {
        val intent = new Intent(MainActivity.this, classOf[IssueListActivity])
        intent.putExtra(IssueListActivity.ExtraRepos, mReposes.get(position).toJSONObject.toString)
        MainActivity.this.startActivity(intent)
      }
    })
    mReposListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)
    mSettings = Settings(this)
    if(mOptionsMenu != null) {
      if(mSettings.startedService) {
        mOptionsMenu.findItem(R.id.startItem).asInstanceOf[MenuItem].setVisible(false)
        mOptionsMenu.findItem(R.id.stopItem).asInstanceOf[MenuItem].setVisible(true)
      } else {
        mOptionsMenu.findItem(R.id.startItem).asInstanceOf[MenuItem].setVisible(true)
        mOptionsMenu.findItem(R.id.stopItem).asInstanceOf[MenuItem].setVisible(false)      
      }
    }
  }
  
  override def onCreateDialog(id: Int, bundle: Bundle) =
    id match {
      case MainActivity.DialogAddRepos       =>
        val builder = new AlertDialog.Builder(this)
        builder.setTitle(R.string.add_repos_title)
        builder.setView(getLayoutInflater().inflate(R.layout.add_repos, null))
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
            val userNameEditText = dialog.asInstanceOf[AlertDialog].findViewById(R.id.userNameEditText).asInstanceOf[EditText]
            val nameEditText = dialog.asInstanceOf[AlertDialog].findViewById(R.id.nameEditText).asInstanceOf[EditText]
            val userName = userNameEditText.getText().toString
            val name = nameEditText.getText().toString
            userNameEditText.setText("")
            nameEditText.setText("")
            addRepository(Repository(GitHubServer(), userName, name))
          }
        })
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
          }
        })
        builder.create()
      case MainActivity.DialogDeleteReposes =>
        val checkedItemCount = mReposListView.getCheckedItemCount()
        val title = getResources().getQuantityString(R.plurals.delete_reposes_title, checkedItemCount)
        val msg = getResources().getQuantityString(R.plurals.delete_reposes_title, checkedItemCount)
        buildQuestionDialog(this, title, msg, true) {
          () =>
            val checkedItems = mReposListView.getCheckedItemPositions()
            val reposIndexes = BitSet() ++ (0 until checkedItems.size()).flatMap {
              i =>
                val key = checkedItems.keyAt(i)
                if(checkedItems.get(key)) BitSet(key) else BitSet()
            }
            mReposListView.clearChoices()
            deleteRepositories(reposIndexes)
        }
      case MainActivity.DialogIOError       =>
        buildErrorDialog(this, getResources().getString(R.string.io_error_message))
      case _                                =>
        super.onCreateDialog(id, bundle)
    }
  
  override def onPrepareDialog(id: Int, dialog: Dialog, bundle: Bundle)
  {
    id match {
      case MainActivity.DialogDeleteReposes =>
        val checkedItemCount = mReposListView.getCheckedItemCount()
        dialog.setTitle(getResources().getQuantityString(R.plurals.delete_reposes_title, checkedItemCount))
        dialog.asInstanceOf[AlertDialog].setMessage(getResources().getQuantityString(R.plurals.delete_reposes_title, checkedItemCount))
      case _                                => ()
    }
    super.onPrepareDialog(id, dialog, bundle)
  }
    
  override def onCreateOptionsMenu(menu: Menu) = {
    getMenuInflater().inflate(R.menu.main_menu, menu)
    mOptionsMenu = menu
    mSettings = if(mSettings == null) Settings(this) else mSettings
    if(mSettings.startedService) {
      mOptionsMenu.findItem(R.id.startItem).asInstanceOf[MenuItem].setVisible(false)
      mOptionsMenu.findItem(R.id.stopItem).asInstanceOf[MenuItem].setVisible(true)
    } else {
      mOptionsMenu.findItem(R.id.startItem).asInstanceOf[MenuItem].setVisible(true)
      mOptionsMenu.findItem(R.id.stopItem).asInstanceOf[MenuItem].setVisible(false)      
    }
    true
  }
  
  override def onOptionsItemSelected(item: MenuItem) = {
    item.getItemId() match {
      case R.id.addReposItem      =>
        showDialog(MainActivity.DialogAddRepos)
        true
      case R.id.deleteReposesItem =>
        showDialog(MainActivity.DialogDeleteReposes)
        true
      case R.id.startItem         =>
        mSettings.startedService = true
        startService(new Intent(this, classOf[MainService]))
        mOptionsMenu.findItem(R.id.startItem).asInstanceOf[MenuItem].setVisible(false)
        mOptionsMenu.findItem(R.id.stopItem).asInstanceOf[MenuItem].setVisible(true)
        true
      case R.id.stopItem          =>
        mSettings.startedService = false
        stopService(new Intent(this, classOf[MainService]))
        mOptionsMenu.findItem(R.id.startItem).asInstanceOf[MenuItem].setVisible(true)
        mOptionsMenu.findItem(R.id.stopItem).asInstanceOf[MenuItem].setVisible(false)
        true
      case R.id.settingsItem      =>
        startActivity(new Intent(this, classOf[SettingsActivity]))
        true
      case _                      =>
        super.onOptionsItemSelected(item)
    }
  }
  
  private def addRepository(repos: Repository)
  {
    mReposes.add(repos)
    mReposListAdapter.notifyDataSetChanged()
    log(mTag, storeRepositories(this, vectorFromList(mReposes))) match {
      case Left(_) => showDialog(MainActivity.DialogIOError)
      case _       => ()
    }
  }
  
  private def deleteRepositories(reposIndexes: BitSet)
  {
    for(i <- reposIndexes) mReposes.remove(i)
    mReposListAdapter.notifyDataSetChanged()
    log(mTag, storeRepositories(this, vectorFromList(mReposes))) match {
      case Left(_) => showDialog(MainActivity.DialogIOError)
      case _       => ()
    }
  }
  
  private def vectorFromList[T](xs: List[T]) = (0 until xs.size()).foldLeft(Vector[T]()) { (ys, i) => ys :+ xs.get(i) }  
}

object MainActivity
{
  private val DialogAddRepos = 0
  private val DialogDeleteReposes = 1
  private val DialogIOError = 2
  
  private class RepositoryListAdapter(activity: Activity, reposes: ArrayList[Repository]) extends ArrayAdapter[Repository](activity, R.layout.repos_item, reposes)
  { 
    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      var view = convertView
      val listView = parent.asInstanceOf[ListView]
      if(convertView == null) {
        view = activity.getLayoutInflater().inflate(R.layout.repos_item, null)
        val textView = view.findViewById(R.id.reposItemTextView).asInstanceOf[TextView]
        textView.setOnClickListener(new View.OnClickListener() {
          override def onClick(view: View)
          {
            listView.performItemClick(convertView, position, getItemId(position)) 
          }
        })
        val checkBox = view.findViewById(R.id.reposItemCheckBox).asInstanceOf[CheckBox]
        view.setTag(RepositoryListAdapter.ViewHolder(textView, checkBox))
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
          override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean)
          {
            listView.setItemChecked(position, isChecked)
          }
        })
      }
      val viewHolder = view.getTag().asInstanceOf[RepositoryListAdapter.ViewHolder]
      viewHolder.textView.setText(stringFromRepository(reposes.get(position)))
      viewHolder.checkBox.setChecked(listView.isItemChecked(position))
      view
    }
  }
  
  private object RepositoryListAdapter
  {
    private case class ViewHolder(textView: TextView, checkBox: CheckBox)
  }
  
  private def stringFromRepository(repos: Repository) = repos.userName + "/" + repos.name
}
