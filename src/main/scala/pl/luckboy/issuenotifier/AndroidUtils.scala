/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.os.Handler
import android.util.Log

object AndroidUtils
{
  case class StopFlag(var b: Boolean)  
  
  def startThreadAndPost[T](handler: Handler, stopFlag: StopFlag)(f: () => T)(g: (T, StopFlag) => Unit)
  {
    new Thread(new Runnable() {
      override def run()
      {
        val res = f()
        handler.post(new Runnable() {
          override def run()
          {
            if(!stopFlag.b) g(res, stopFlag)
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
  
  def log(tag: String, s: String) = { Log.i(tag, s); () }
  
  def log[T](tag: String, res: Either[Exception, T]) = 
    res match {
      case Left(e) => Log.w(tag, e); res
      case _       => res
    }
}
