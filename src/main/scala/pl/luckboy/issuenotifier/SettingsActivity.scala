/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import DataStorage._

class SettingsActivity extends PreferenceActivity with TypedActivity
{
  private val mTag = getClass().getSimpleName()
    
  private var mPreference: Preference = null
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    addPreferencesFromResource(R.xml.settings_screen)
    mPreference = getPreferenceManager().findPreference("settings_delete_timestamps")
    mPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      override def onPreferenceClick(preference: Preference) =
        if(preference.getKey().equals("settings_delete_timestamps")) {
          SettingsActivity.this.showDialog(SettingsActivity.DialogDeleteTimestamps)
          true
        } else
          false
    })
  }

  override def onCreateDialog(id: Int, bundle: Bundle) = {
    id match {
      case SettingsActivity.DialogDeleteTimestamps =>
        val builder = new AlertDialog.Builder(this)
        builder.setTitle(getResources().getString(R.string.delete_timestamps_title))
        builder.setMessage(getResources().getString(R.string.delete_timestamps_message))
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
        	clearAllRepositoryTimestampInfos(SettingsActivity.this)
          }
        })
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
          }
        })
        builder.create()
        
    }
  }
}

object SettingsActivity
{
  private val DialogDeleteTimestamps = 0
}