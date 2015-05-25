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
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpConnectionParams
import org.json.JSONArray
import org.json.JSONObject

object HttpUtils
{
  def getInputStream[T](uri: URI, timeout: Option[Int], headers: Map[String, String]) =
    try {
      val params = new BasicHttpParams()
      for(t <- timeout) HttpConnectionParams.setConnectionTimeout(params, t)
      val client = new DefaultHttpClient()
      val request = new HttpGet(uri)
      request.setHeader("User-Agent", "IssueNotifier")
      for(p <- headers) request.setHeader(p._1, p._2)
      val response = client.execute(request)
      if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
        Right(response.getEntity().getContent())
      else
        Left(HttpStatusException(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()))
    } catch {
      case e: Exception => Left(e)
    }
  
  def getString(uri: URI, timeout: Option[Int], headers: Map[String, String]) =
    try {
      getInputStream(uri, timeout, headers) match {
        case Left(e)   => Left(e)
        case Right(is) => 
          try {
        	Right(new Scanner(is, "UTF-8").useDelimiter("\\A").next())
          } finally {
            is.close()
          }
      }
    } catch {
      case e: Exception => Left(e)
    }
  
  def getJSONObject(uri: URI, timeout: Option[Int], headers: Map[String, String]) =
    try {
      getString(uri, timeout, headers) match {
        case Left(e)  => Left(e)
        case Right(s) => Right(new JSONObject(s))
      }
    } catch {
      case e: Exception => Left(e)
    }

  def getJSONArray(uri: URI, timeout: Option[Int], headers: Map[String, String]) =
    try {
      getString(uri, timeout, headers) match {
        case Left(e)  => Left(e)
        case Right(s) => Right(new JSONArray(s))
      }
    } catch {
      case e: Exception => Left(e)
    }
  
  def encode(s: String) = URLEncoder.encode(s, "UTF-8")

  def stringFromParams(params: Map[String, String]) =
    params.map { case (k, v) => encode(k) + "=" + encode(v) }.mkString("&")  
}

case class HttpStatusException(code: Int, phrase: String) extends Exception
{
  override def getMessage() = code + " " + phrase
}
