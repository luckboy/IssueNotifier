/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.util.Scanner
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject

object HttpUtils
{
  def getInputStream[T](uri: URI) =
    try {
      val client = new DefaultHttpClient()
      val request = new HttpGet(uri)
      val response = client.execute(request)
      Right(response.getEntity().getContent())
    } catch {
      case e: Exception => Left(e)
    }
  
  def getString(uri: URI) =
    try {
      getInputStream(uri) match {
        case Left(e)   => Left(e)
        case Right(is) => 
          try {
        	Right(new Scanner(is, "UTF-8").useDelimiter("\\Z").next())
          } finally {
            is.close()
          }
      }
    } catch {
      case e: Exception => Left(e)
    }
    
  def getJSONObject(uri: URI) =
    getString(uri) match {
      case Left(e)  => Left(e)
      case Right(s) => Right(new JSONObject(s))
    }

  def getJSONArray(uri: URI) =
    getString(uri) match {
      case Left(e)  => Left(e)
      case Right(s) => Right(new JSONArray(s))
    }
  
  def encode(s: String) = URLEncoder.encode(s, "UTF-8")

  def stringFromParams(params: Map[String, String]) =
    params.map { case (k, v) => encode(k) + "=" + encode(v) }.mkString("&")  
}
