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

class MainReceiver extends BroadcastReceiver
{
  private val mTag = getClass().getSimpleName()
  
  override def onReceive(context: Context, intent: Intent)
  {
    log(mTag, "onReceive(): intent.getAction() = " + intent.getAction())
    val activityIntent = new Intent(context, classOf[IssuePairListActivity])
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(activityIntent)
  }
}

object MainReceiver
{
  val ActionIssuePairs = classOf[MainReceiver].getName() + ".ActionIssuePairs"
}
