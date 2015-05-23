/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import com.github.rjeschke.txtmark
import HttpUtils._

class GitHubDataFetcher(val apiURI: String) extends DataFetcher
{
  override val defaultPerPage = 10
  
  private def stringFromState(state: State.Value) =
    state match {
      case State.Open   => "open"
      case State.Closed => "closed"
      case State.All    => "all"
    }
  
  private def stringFromIssueSorting(sorting: IssueSorting.Value) =
    sorting match {
      case IssueSorting.Created  => "created"
      case IssueSorting.Updated  => "updated"
      case IssueSorting.Comments => "comments"
    }
  
  private def stringFromDirection(dir: Direction.Value) =
    dir match {
      case Direction.Asc  => "asc"
      case Direction.Desc => "desc"
    }

  private val simpleDateFormat = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf
  }
  
  private def stringFromSince(since: Date) = simpleDateFormat.format(since)
  
  private def stateFromString(s: String) =
    s match {
      case "open"   => State.Open
      case "closed" => State.Closed
      case "all"    => State.All
    }
  
  private val apiRequestHeaders = Map("Accept" -> "application/vnd.github.v3+json")
  
  override def fetchIssue(repos: Repository, issueInfo: IssueInfo, timeout: Option[Int]): Either[Exception, Issue] = {
    val uri = apiURI + "/repos/" + encode(repos.userName) + "/" + encode(repos.name) + "/issues/" ++ encode(issueInfo.number.toString)
    getJSONObject(new URI(uri), timeout, apiRequestHeaders) match {
      case Left(e)           => Left(e)
      case Right(jsonObject) => issueFromJSONObject(jsonObject)
    }
  }
  
  private def issueFromJSONObject(jsonObject: JSONObject) =
    try {
      issueInfoFromJSONObject(jsonObject : JSONObject) match {
        case Left(e)          => Left(e)
        case Right(issueInfo) =>
          val bodyHtml = txtmark.Processor.process(jsonObject.getString("body"))
          Right(Issue(issueInfo, bodyHtml))
      }
    } catch {
      case e: Exception => Left(e)
    }
    
  private def userFromJSONObject(jsonObject: JSONObject) =
    try {
      val id = jsonObject.getLong("id").toString
      val name = jsonObject.getString("login")
      val avatarURI = jsonObject.getString("avatar_url")
      Right(User(id, name, avatarURI))
    } catch {
      case e: Exception => Left(e)
    }


  override def fetchIssueInfos(repos: Repository, state: Option[State.Value], sorting: Option[IssueSorting.Value], dir: Option[Direction.Value], since: Option[Date], page: Option[Long], perPage: Option[Long], timeout: Option[Int]): Either[Exception, Vector[IssueInfo]] = {
    val paramMap = Map("per_page" -> perPage.getOrElse(defaultPerPage).toString) ++
    		state.map { s => ("state" -> stringFromState(s)) } ++
    		sorting.map { s => ("sort" -> stringFromIssueSorting(s)) } ++
    		dir.map { d => ("direction" -> stringFromDirection(d)) } ++
    		since.map { s => ("since" -> stringFromSince(s)) } ++
    		page.map { p => ("page" -> p.toString) }
    val paramMapStr = stringFromParams(paramMap)
    val uri = apiURI + "/repos/" + encode(repos.userName) + "/" + encode(repos.name) + "/issues" + (if(paramMapStr != "") "?" + paramMapStr else "")
    getJSONArray(new URI(uri), timeout, apiRequestHeaders) match {
      case Left(e)          => Left(e)
      case Right(jsonArray) =>
        (0 until jsonArray.length()).foldLeft(Right(Vector()): Either[Exception, Vector[IssueInfo]]) {
          case (Left(e), _)       => Left(e)
          case (Right(issueInfos), i) =>
            issueInfoFromJSONObject(jsonArray.getJSONObject(i)) match {
              case Left(e)          => Left(e)
              case Right(issueInfo) => Right(issueInfos :+ issueInfo)
            }
        }
    }
  }
  
  private def issueInfoFromJSONObject(jsonObject: JSONObject) =
    try {
      val id = jsonObject.getLong("id").toString
      val number = jsonObject.getLong("number")
      val state = stateFromString(jsonObject.getString("state"))
      val title = jsonObject.getString("title")
      val commentCount = jsonObject.getLong("comments")
      val closedAtStr = jsonObject.optString("closed_at")
      val createdAtStr = jsonObject.getString("created_at")
      val updatedAtStr = jsonObject.getString("updated_at")
      val closedAt = if(closedAtStr == "") Some(simpleDateFormat.parse(closedAtStr)) else None
      val createdAt = simpleDateFormat.parse(createdAtStr)
      val updatedAt =  simpleDateFormat.parse(updatedAtStr)
      userFromJSONObject(jsonObject.getJSONObject("user")) match {
        case Left(e)     => Left(e)
        case Right(user) => Right(IssueInfo(id, number, state, title, commentCount, user, closedAt, createdAt, updatedAt))
      }
    } catch {
      case e: Exception => Left(e)
    }
      
  private def stringFromCommentSorting(sorting: CommentSorting.Value) =
    sorting match {
      case CommentSorting.Created  => "created"
      case CommentSorting.Updated  => "updated"
    }
  
  override def fetchComments(repos: Repository, issueInfo: IssueInfo, sorting: Option[CommentSorting.Value], dir: Option[Direction.Value], since: Option[Date], page: Option[Long], perPage: Option[Long], timeout: Option[Int]): Either[Exception, Vector[Comment]] = {
    val paramMap = Map("per_page" -> perPage.getOrElse(defaultPerPage).toString) ++
    		sorting.map { s => ("sort" -> stringFromCommentSorting(s)) } ++
    		dir.map { d => ("direction" -> stringFromDirection(d)) } ++
    		since.map { s => ("since" -> stringFromSince(s)) } ++
    		page.map { p => ("page" -> p.toString) }
    val paramMapStr = stringFromParams(paramMap)
    val uri = apiURI + "/repos/" + encode(repos.userName) + "/" + encode(repos.name) + "/issues/" + encode(issueInfo.number.toString) + "/comments" + (if(paramMapStr != "") "?" + paramMapStr else "")
    getJSONArray(new URI(uri), timeout, apiRequestHeaders) match {
      case Left(e)          => Left(e)
      case Right(jsonArray) =>
        (0 until jsonArray.length()).foldLeft(Right(Vector()): Either[Exception, Vector[Comment]]) {
          case (Left(e), _)         => Left(e)
          case (Right(comments), i) =>
            commentFromJSONObject(jsonArray.getJSONObject(i)) match {
              case Left(e)        => Left(e)
              case Right(comment) => Right(comments :+ comment)
            }
        }
    }
  }

  private def commentFromJSONObject(jsonObject: JSONObject) =
    try {
      val id = jsonObject.getLong("id").toString
      val bodyHtml = txtmark.Processor.process(jsonObject.getString("body"))
      val createdAtStr = jsonObject.getString("created_at")
      val updatedAtStr = jsonObject.getString("updated_at")
      val createdAt = simpleDateFormat.parse(createdAtStr)
      val updatedAt =  simpleDateFormat.parse(updatedAtStr)
      userFromJSONObject(jsonObject.getJSONObject("user")) match {
        case Left(e)     => Left(e)
        case Right(user) => Right(Comment(id, bodyHtml, user, createdAt, updatedAt))
      }
    } catch {
      case e: Exception => Left(e)
    }
}
