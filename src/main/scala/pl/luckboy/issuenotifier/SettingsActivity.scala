/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import android.text.Html
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import AndroidUtils._
import DataStorage._

class SettingsActivity extends PreferenceActivity with TypedActivity
{
  private val mTag = getClass().getSimpleName()
  
  private var mDeleteTimestampsPreference: Preference = null

  private var mAboutPreference: Preference = null
  
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    addPreferencesFromResource(R.xml.settings_screen)
    mDeleteTimestampsPreference = getPreferenceManager().findPreference("settings_delete_timestamps")
    mDeleteTimestampsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      override def onPreferenceClick(preference: Preference) =
        if(preference.getKey().equals("settings_delete_timestamps")) {
          SettingsActivity.this.showDialog(SettingsActivity.DialogDeleteTimestamps)
          true
        } else
          false
    })
    mAboutPreference = getPreferenceManager().findPreference("settings_about")
    mAboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      override def onPreferenceClick(preference: Preference) =
        if(preference.getKey().equals("settings_about")) {
          SettingsActivity.this.showDialog(SettingsActivity.DialogAbout)
          true
        } else
          false
    })
  }

  override def onCreateDialog(id: Int, bundle: Bundle) =
    id match {
      case SettingsActivity.DialogDeleteTimestamps =>
        val title = getResources().getString(R.string.delete_timestamps_title)
        val msg = getResources().getString(R.string.delete_timestamps_message)
        createQuestionDialog(this, title, msg, true) {
          () => clearAllRepositoryTimestampInfos(SettingsActivity.this)
        }
      case SettingsActivity.DialogAbout            =>
        val builder = new AlertDialog.Builder(this)
        builder.setIcon(android.R.drawable.ic_dialog_info)
        builder.setTitle(getResources().getString(R.string.about_title))
        builder.setView(getLayoutInflater().inflate(R.layout.about, null))
        builder.setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int) = ()
        })
        val licenseId = R.string.about_license
        val onLicenseClickListener = new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
            SettingsActivity.this.startActivity(new Intent(SettingsActivity.this, classOf[LicenseActivity]))
          }
        }
        val thirdPartyId = R.string.about_third_party
        val onThirdPartyClickListener = new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, id: Int)
          {
            SettingsActivity.this.startActivity(new Intent(SettingsActivity.this, classOf[ThirdPartyActivity]))
          }
        }
        if(Build.VERSION.SDK_INT >= 14) {
          builder.setNeutralButton(licenseId, onLicenseClickListener)
          builder.setPositiveButton(thirdPartyId, onThirdPartyClickListener)
        } else {
          builder.setNeutralButton(thirdPartyId, onThirdPartyClickListener)
          builder.setPositiveButton(licenseId, onLicenseClickListener)
        }
        builder.create()
      case _                                       =>
        super.onCreateDialog(id, bundle)
    }
  
  override def onPrepareDialog(id: Int, dialog: Dialog, bundle: Bundle)
  {
    id match {
      case SettingsActivity.DialogAbout =>
        val textView = dialog.findViewById(R.id.aboutTextView).asInstanceOf[TextView]
        textView.setMovementMethod(new ScrollingMovementMethod())
        textView.setText(new SpannableString(Html.fromHtml(getResources().getString(R.string.about_message))))
      case _                            => ()
    }
    super.onPrepareDialog(id, dialog, bundle)
  }
}

object SettingsActivity
{
  private val DialogDeleteTimestamps = 0
  private val DialogAbout = 1
}
