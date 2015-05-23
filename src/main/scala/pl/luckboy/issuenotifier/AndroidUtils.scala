/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.util.Log

object AndroidUtils
{
  case class StopFlag(var b: Boolean)  
  
  def startThreadAndPost[T](handler: Handler, stopFlag: StopFlag)(f: () => T)(g: T => Unit)
  {
    new Thread(new Runnable() {
      override def run()
      {
        val res = f()
        handler.post(new Runnable() {
          override def run()
          {
            if(!stopFlag.b) g(res)
          }
        })
      }
    }).start()
  }
  
  def post(handler: Handler)(f: () => Unit)
  {
    handler.post(new Runnable() {
      override def run()
      {
        f()
      }
    })
  }
  
  def postDelayed(handler: Handler, millis: Int)(f: () => Unit)
  {
    handler.postDelayed(new Runnable() {
      override def run()
      {
        f()
      }
    }, millis)
  }
  
  def notify(context: Context, smallIconId: Int, title: String, body: String, optPendingIntent: Option[PendingIntent], isAutoCancel: Boolean)
  {
    val builder = new Notification.Builder(context)
    builder.setSmallIcon(smallIconId)
    builder.setContentTitle(title)
    builder.setContentText(body)
    for(pendingIntent <- optPendingIntent) builder.setContentIntent(pendingIntent)
    val notification = builder.getNotification()
    notification.flags |= (if(isAutoCancel) Notification.FLAG_AUTO_CANCEL else 0) | Notification.FLAG_NO_CLEAR 
    val nofificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    nofificationManager.notify(0, notification)
  }
  
  def log(tag: String, s: String) = { Log.i(tag, s); () }
  
  def log[T](tag: String, res: Either[Exception, T]) = 
    res match {
      case Left(e) => Log.w(tag, e); res
      case _       => res
    }
}
