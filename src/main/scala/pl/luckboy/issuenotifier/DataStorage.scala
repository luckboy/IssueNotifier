/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.content.Context
import java.io.FileNotFoundException
import java.io.OutputStreamWriter
import java.util.Scanner
import org.json.JSONArray
import org.json.JSONObject

object DataStorage
{
  private def jsonObjectFromPair[T <: JSONable, U <: JSONable](pair: (T, U)) = {
    val jsonObject = new JSONObject()
    jsonObject.put("_1", pair._1.toJSONObject)
    jsonObject.put("_2", pair._2.toJSONObject)
  }

  private def pairFromJSONObject[T, U](jsonObject: JSONObject)(implicit f: JSONObject => Either[Exception, T], g: JSONObject => Either[Exception, U]) =
   f(jsonObject.getJSONObject("_1")) match {
     case Left(e)  => Left(e)
     case Right(x) => 
       g(jsonObject.getJSONObject("_2")) match {
         case Left(e) => Left(e)
         case Right(y) => Right((x, y))
       }
   }
  
  private def jsonArrayFromIterable[T <: JSONable](xs: Iterable[T]) = {
    val jsonArray = new JSONArray()
    var i = 0
    for(x <- xs) {
      jsonArray.put(i, x.toJSONObject)
      i += 1
    }
    jsonArray    
  }
  
  private def vectorFromJSONArray[T](jsonArray: JSONArray)(implicit f: JSONObject => Either[Exception, T]) =
    (0 until jsonArray.length()).foldLeft(Right(Vector()): Either[Exception, Vector[T]]) {
      case (Left(e), _)      => Left(e)
      case (Right(pairs), i) =>
        f(jsonArray.getJSONObject(i)) match {
          case Left(e)     => Left(e)
          case Right(pair) => Right(pairs :+ pair)
        }
    }
  
  private def jsonArrayFromPairs[T <: JSONable, U <: JSONable](pairs: Iterable[(T, U)]) = {
    val jsonArray = new JSONArray()
    var i = 0
    for(pair <- pairs) {
      jsonArray.put(i, jsonObjectFromPair(pair))
      i += 1
    }
    jsonArray
  }
  
  private def pairsFromJSONArray[T, U](jsonArray: JSONArray)(implicit f: JSONObject => Either[Exception, T], g: JSONObject => Either[Exception, U]) =
    (0 until jsonArray.length()).foldLeft(Right(Vector()): Either[Exception, Vector[(T, U)]]) {
      case (Left(e), _)      => Left(e)
      case (Right(pairs), i) =>
        pairFromJSONObject(jsonArray.getJSONObject(i))(f, g) match {
          case Left(e)     => Left(e)
          case Right(pair) => Right(pairs :+ pair)
        }
    }
  
  def loadRepositories(context: Context): Either[Exception, Vector[Repository]] =
    try {
      val fis = context.openFileInput("rs.json")
      try {
        val s = new Scanner(fis, "UTF-8").useDelimiter("\\Z").next()
        vectorFromJSONArray[Repository](new JSONArray(s))
      } finally {
        fis.close()
      }
    } catch {
      case e: FileNotFoundException => Right(Vector())
      case e: Exception             => Left(e)
    }
  
  def storeRepositories(context: Context, reposes: Vector[Repository]): Either[Exception, Unit] =
    try {
      val osw = new OutputStreamWriter(context.openFileOutput("rs.json", Context.MODE_PRIVATE))
      try {
    	osw.write(jsonArrayFromIterable(reposes).toString)
        Right(())
      } finally {
        osw.close()
      }
    } catch {
      case e: Exception => Left(e)
    }
  
  private def loadRepositoryTimestampInfos(context: Context, fileName: String): Either[Exception, Map[Repository, RepositoryTimestampInfo]] =
    try {
      val fis = context.openFileInput(fileName)
      try {
        val s = new Scanner(fis, "UTF-8").useDelimiter("\\Z").next()
        pairsFromJSONArray[Repository, RepositoryTimestampInfo](new JSONArray(s)) match {
          case Left(e)      => Left(e)
         case Right(pairs) => Right(pairs.toMap)
        }
      } finally {
        fis.close()
      }
    } catch {
      case e: FileNotFoundException => Right(Map())
      case e: Exception             => Left(e)
    }
  
  private def storeRepositoryTimestampInfos(context: Context, fileName: String, infos: Map[Repository, RepositoryTimestampInfo]): Either[Exception, Unit] =
    try {
      val osw = new OutputStreamWriter(context.openFileOutput(fileName, Context.MODE_PRIVATE))
      try {
    	osw.write(jsonArrayFromPairs(infos).toString)
        Right(())
      } finally {
        osw.close()
      }
    } catch {
      case e: Exception => Left(e)
    }

  def loadLastRepositoryTimestampInfos(context: Context): Either[Exception, Map[Repository, RepositoryTimestampInfo]] =
    loadRepositoryTimestampInfos(context, "lrtis.json")
  
  def storeLastRepositoryTimestampInfos(context: Context, infos: Map[Repository, RepositoryTimestampInfo]): Either[Exception, Unit] =
    storeRepositoryTimestampInfos(context, "lrtis.json", infos)
    
  def loadOldRepositoryTimestampInfos(context: Context): Either[Exception, Map[Repository, RepositoryTimestampInfo]] =
    loadRepositoryTimestampInfos(context, "ortis.json")

  def storeOldRepositoryTimestampInfos(context: Context, infos: Map[Repository, RepositoryTimestampInfo]): Either[Exception, Unit] =
    storeRepositoryTimestampInfos(context, "ortis.json", infos)
}
