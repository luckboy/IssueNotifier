/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import AndroidUtils._

class BootReceiver extends BroadcastReceiver 
{
  private val mTag = getClass().getSimpleName()

  override def onReceive(context: Context, intent: Intent)
  {
    log(mTag, "onReceive(): intent.getAction() = " + intent.getAction())
    if(Settings(context).startedService) context.startService(new Intent(context, classOf[MainService]))
  }
}
