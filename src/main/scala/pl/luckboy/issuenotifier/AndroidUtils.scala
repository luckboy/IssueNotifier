/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.os.Handler
import android.text.Html
import android.text.SpannedString
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
  
  def createQuestionDialog(context: Context, title: String, msg: String, isWarning: Boolean = false)(f: () => Unit) ={
    val builder = new AlertDialog.Builder(context)
    if(isWarning) builder.setIcon(android.R.drawable.ic_dialog_alert)
    builder.setTitle(title)
    builder.setMessage(msg)
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, id: Int) = f()
    })
    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, id: Int) = ()
    })
    builder.create()
  }
  
  def createErrorDialog(context: Context, msg: String) = {
    val builder = new AlertDialog.Builder(context)
    builder.setIcon(android.R.drawable.ic_dialog_alert)
    builder.setTitle(R.string.error_title)
    builder.setMessage(msg)
    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, id: Int) = ()
    })
    builder.create()
  }
  
  def notify(context: Context, id: Int, smallIconId: Int, largeIconId: Option[Int], title: String, body: String, optPendingIntent: Option[PendingIntent], isTicker: Boolean, isAutoCancel: Boolean, isRingtone: Boolean, isVibration: Boolean)
  {
    val builder = new Notification.Builder(context)
    builder.setSmallIcon(smallIconId)
    for(id <- largeIconId) builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), id))
    builder.setContentTitle(title)
    builder.setContentText(body)
    if(isTicker) builder.setTicker(body)
    for(pendingIntent <- optPendingIntent) builder.setContentIntent(pendingIntent)
    val notification = builder.getNotification()
    notification.flags |= (if(isAutoCancel) Notification.FLAG_AUTO_CANCEL else 0) | Notification.FLAG_NO_CLEAR
    if(isRingtone) notification.defaults |= Notification.DEFAULT_SOUND
    if(isVibration) notification.defaults |= Notification.DEFAULT_VIBRATE
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    notificationManager.notify(id, notification)
  }
  
  def cancleNotification(context: Context, id: Int)
  {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    notificationManager.cancel(id)
  }
  
  def log(tag: String, s: String) = { Log.i(tag, s); () }
  
  def log[T](tag: String, res: Either[Exception, T]) = 
    res match {
      case Left(e) => Log.w(tag, e); res
      case _       => res
    }
  
  def htmlFromString(s: String) = Html.toHtml(new SpannedString(s))
}
