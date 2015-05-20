/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

class Settings(preferences: SharedPreferences)
{
  def interval = preferences.getString("settings_interval", "30000").toInt
  
  def state =
    preferences.getString("settings_state", "0").toInt match {
      case 1 => State.Closed 
      case 2 => State.All
      case _ => State.Open
    }
  
  def sortingByCreated = preferences.getBoolean("settings_sorting_by_created", false)
}

object Settings
{
  def apply(context: Context) = new Settings(PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()))
}
