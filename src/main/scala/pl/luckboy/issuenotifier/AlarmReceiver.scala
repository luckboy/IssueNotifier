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
import android.os.PowerManager
import AndroidUtils._

class AlarmReceiver extends BroadcastReceiver
{
  private val mTag = getClass().getSimpleName()
  
  override def onReceive(context: Context, intent: Intent)
  {
    log(mTag, "onReceive(): intent.getAction() = " + intent.getAction())
    val powerManager = context.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmReceiverWakeLock")
    wakeLock.acquire()
    AlarmReceiver.sWakeLock = Some(wakeLock)
    context.startService(new Intent(context, classOf[MainService]))
  }
}

object AlarmReceiver
{
  private var sWakeLock: Option[PowerManager#WakeLock] = None
  
  def releaseWakeLock() = {
    for(wl <- sWakeLock) wl.release()
    sWakeLock = None
  }
}
