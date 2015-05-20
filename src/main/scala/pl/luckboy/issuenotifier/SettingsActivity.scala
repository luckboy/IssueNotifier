/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.os.Bundle
import android.preference.PreferenceActivity

class SettingsActivity extends PreferenceActivity with TypedActivity
{
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    addPreferencesFromResource(R.xml.settings_screen)
  }
}
