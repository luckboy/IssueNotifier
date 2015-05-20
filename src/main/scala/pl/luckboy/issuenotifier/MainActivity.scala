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
import scala.collection.immutable.BitSet
import scala.collection.mutable.ArrayBuffer
import AndroidUtils._
import DataStorage._

class MainActivity extends Activity with TypedActivity
{
  private val mTag = getClass().getSimpleName()
    
  private var mOptionsMenu: Menu = null
  private var mReposListView: ListView = null
  private var mReposList: ArrayList[Repository] = null
  private var mReposListAdapter: MainActivity.RepositoryAdapter = null
  private var mReposes: ArrayBuffer[Repository] = null
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    setContentView(R.layout.main)
    mReposes = ArrayBuffer() ++ log(mTag, loadRepositories(this)).fold(_ => Vector(), identity)
    mReposListView = findView(TR.reposListView)
    mReposList = new ArrayList[Repository]()
    for(repos <- mReposes) mReposList.add(repos)
    mReposListAdapter = new MainActivity.RepositoryAdapter(this, mReposList)
    mReposListView.setAdapter(mReposListAdapter)
    mReposListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long)
      {
      }
    })
    mReposListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)
  }
  
  override def onCreateDialog(id: Int, bundle: Bundle) = {
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
            updateReposListView()
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
        val builder = new AlertDialog.Builder(this)
        builder.setTitle(getResources().getQuantityString(R.plurals.delete_reposes_title, checkedItemCount))
        builder.setMessage(getResources().getQuantityString(R.plurals.delete_reposes_title, checkedItemCount))
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
            val checkedItems = mReposListView.getCheckedItemPositions()
            val reposIndexes = BitSet() ++ (0 until checkedItems.size()).flatMap {
              i =>
                val key = checkedItems.keyAt(i)
                if(checkedItems.get(key)) BitSet(key) else BitSet()
            }
            mReposListView.clearChoices()
            deleteRepositories(reposIndexes)
            updateReposListView()
          }
        })
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
          }
        })
        builder.create()
      case _                                =>
        super.onCreateDialog(id, bundle)
    }
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
        //startService(new Intent(this, classOf[MainService]))
        mOptionsMenu.findItem(R.id.startItem).asInstanceOf[MenuItem].setVisible(false)
        mOptionsMenu.findItem(R.id.stopItem).asInstanceOf[MenuItem].setVisible(true)
        true
      case R.id.stopItem          =>
        //stopService(new Intent(this, classOf[MainService]))
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
    mReposes += repos
    log(mTag, storeRepositories(this, Vector() ++ mReposes))
  }
  
  private def deleteRepositories(reposIndexes: BitSet)
  {
    mReposes = mReposes.zipWithIndex.filterNot { case (_, i) => reposIndexes.contains(i) }.map { _._1 }
    log(mTag, storeRepositories(this, Vector() ++ mReposes))
  }
  
  private def updateReposListView()
  {
    mReposList.clear()
    for(repos <- mReposes) mReposList.add(repos)
    mReposListAdapter.notifyDataSetChanged()
  }
}

object MainActivity
{
  val DialogAddRepos = 0
  val DialogDeleteReposes = 1
  
  private class RepositoryAdapter(activity: Activity, reposes: ArrayList[Repository]) extends ArrayAdapter[Repository](activity, R.layout.repos_item, reposes)
  { 
    val mReposes = reposes
    
    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      var view = convertView
      val listView = parent.asInstanceOf[ListView]
      if(convertView == null) {
        view = activity.getLayoutInflater().inflate(R.layout.repos_item, null)
        val textView = view.findViewById(R.id.reposItemTextView).asInstanceOf[TextView]
        val checkBox = view.findViewById(R.id.reposItemCheckBox).asInstanceOf[CheckBox]
        view.setTag(RepositoryAdapter.ViewHolder(textView, checkBox))
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
          override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean)
          {
            listView.setItemChecked(position, isChecked)
          }
        })
      }
      val viewHolder = view.getTag().asInstanceOf[RepositoryAdapter.ViewHolder]
      viewHolder.textView.setText(stringFromRepository(mReposes.get(position)))
      viewHolder.checkBox.setChecked(listView.isItemChecked(position))
      view
    }
  }
  
  object RepositoryAdapter
  {
    case class ViewHolder(textView: TextView, checkBox: CheckBox)
  }
  
  private def stringFromRepository(repos: Repository) = repos.userName + "/" + repos.name
}
