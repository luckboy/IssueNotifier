/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONObject

trait JSONable
{
  def toJSONObject: JSONObject
}

case class Repository(
    server: Server,
    userName: String,
    name: String) extends JSONable
{
  override def toJSONObject = {
    val jsonObject = new JSONObject()
    jsonObject.put("server", server.toJSONObject)
    jsonObject.put("userName", userName)
    jsonObject.put("name", name)
  }
}

object Repository
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      Server.fromJSONObject(jsonObject.getJSONObject("server")) match {
        case Left(e)       => Left(e)
        case Right(server) =>
          val userName = jsonObject.getString("userName")
          val name = jsonObject.getString("name")
          Right(Repository(server, userName, name))
      }
    } catch {
      case e: Exception => Left(e)
    }
}

case class RepositoryTimestampInfo(
    createdIssueAt: Date,
    updatedIssueAt: Date) extends JSONable
{
  override def toJSONObject = {
    val sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS")
    val jsonObject = new JSONObject()
    jsonObject.put("createdIssueAt", sdf.format(createdIssueAt))
    jsonObject.put("updatedIssueAt", sdf.format(updatedIssueAt))
  }
}

object RepositoryTimestampInfo
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      val sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS")
      val createdIssueAtStr = jsonObject.getString("createdIssueAt")
      val updatedIssueAtStr = jsonObject.getString("updatedIssueAt")
      val createdIssueAt = sdf.parse(createdIssueAtStr)
      val updatedIssueAt = sdf.parse(updatedIssueAtStr)
      Right(RepositoryTimestampInfo(createdIssueAt, updatedIssueAt))
    }
}

case class IssueInfo(
    id: String,
    number: Long,
    state: State.Value,
    title: String,
    commentCount: Long,
    user: User,
    closedAt: Option[Date],
    createdAt: Date,
    updatedAt: Date) extends JSONable
{
  def isEarlierIssue(reposTimestampInfo: RepositoryTimestampInfo, isSortingByCreated: Boolean) =
    if(!isSortingByCreated)
      updatedAt.compareTo(reposTimestampInfo.updatedIssueAt) > 0
    else
      createdAt.compareTo(reposTimestampInfo.createdIssueAt) > 0
  
  override def toJSONObject = {
    val sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS")
    val jsonObject = new JSONObject()
    jsonObject.put("id", id)
    jsonObject.put("number", number)
    jsonObject.put("state", state.toString)
    jsonObject.put("title", title)
    jsonObject.put("commentCount", commentCount)
    jsonObject.put("user", user.toJSONObject)
    for(date <- closedAt) jsonObject.put("closedAt", sdf.format(date))
    jsonObject.put("createdAt", sdf.format(createdAt))
    jsonObject.put("updatedAt", sdf.format(updatedAt))
  }
}

object IssueInfo
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      val sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS")
      val id = jsonObject.getString("id")
      val number = jsonObject.getLong("number")
      val state = State.withName(jsonObject.getString("state"))
      val title = jsonObject.getString("title")
      val commentCount = jsonObject.getLong("commentCount")
      User.fromJSONObject(jsonObject.getJSONObject("user")) match {
        case Left(e)     => Left(e)
        case Right(user) =>
          val closedAtStr = jsonObject.optString("closedAt")
          val createdAtStr = jsonObject.getString("createdAt")
          val updatedAtStr = jsonObject.getString("updatedAt")
          val closedAt = if(closedAtStr != "") Some(sdf.parse(closedAtStr)) else None
          val createdAt = sdf.parse(createdAtStr)
          val updatedAt = sdf.parse(updatedAtStr)
          Right(IssueInfo(id, number, state, title, commentCount, user, closedAt, createdAt, updatedAt))
      }
    } catch {
      case e: Exception => Left(e)
    }
}

case class Issue(
    info: IssueInfo,
    bodyHtml: String) extends JSONable
{
  override def toJSONObject = {
    val jsonObject = new JSONObject()
    jsonObject.put("info", info.toJSONObject)
    jsonObject.put("bodyHtml", bodyHtml)
  }
}

object Issue
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      IssueInfo.fromJSONObject(jsonObject.getJSONObject("info")) match {
        case Left(e)     => Left(e)
        case Right(info) =>
          Right(Issue(info, jsonObject.getString("bodyHtml")))
      }
    } catch {
      case e: Exception => Left(e)
    }
}

case class IssuePair(repos: Repository, issueInfo: IssueInfo) extends JSONable
{
  override def toJSONObject = {
    val jsonObject = new JSONObject()
    jsonObject.put("repos", repos.toJSONObject)
    jsonObject.put("issueInfo", repos.toJSONObject)
  }
}

object IssuePair
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      Repository.fromJSONObject(jsonObject.getJSONObject("repos")) match {
        case Left(e)      => Left(e)
        case Right(repos) =>
          IssueInfo.fromJSONObject(jsonObject.getJSONObject("issueInfo")) match {
            case Left(e)          => Left(e)
            case Right(issueInfo) => Right(IssuePair(repos, issueInfo))
          }
      }
    } catch {
      case e: Exception => Left(e)
    }
}

case class Comment(
    id: String,
    bodyHtml: String,
    user: User,
    createdAt: Date,
    updatedAt: Date) extends JSONable
{
  override def toJSONObject = {
    val sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS")
    val jsonObject = new JSONObject()
    jsonObject.put("id", id)
    jsonObject.put("bodyHtml", bodyHtml)
    jsonObject.put("user", user.toJSONObject)
    jsonObject.put("createdAt", sdf.format(createdAt))
    jsonObject.put("updatedAt", sdf.format(updatedAt))
  }
}

object Comment
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      val sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS")
      val id =  jsonObject.getString("id")
      val bodyHtml = jsonObject.getString("bodyHtml")
      User.fromJSONObject(jsonObject.getJSONObject("user")) match {
        case Left(e)     => Left(e)
        case Right(user) =>
          val createdAtStr = jsonObject.getString("createdAt")
          val updatedAtStr = jsonObject.getString("updatedAt")
          val createdAt = sdf.parse(createdAtStr)
          val updatedAt = sdf.parse(updatedAtStr)
          Right(Comment(id, bodyHtml, user, createdAt, updatedAt))
      }
    } catch {
      case e: Exception => Left(e)
    }
}

case class User(
    id: String,
    name: String,
    avatarURI: String) extends JSONable
{
  def toJSONObject = {
    val jsonObject = new JSONObject()
    jsonObject.put("id", id)
    jsonObject.put("name", name)
    jsonObject.put("avatarURI", avatarURI)
  }
}

object User
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      val id = jsonObject.getString("id")
      val name = jsonObject.getString("name")
      val avatarURI = jsonObject.getString("avatarURI")
      Right(User(id, name, avatarURI))
    } catch {
      case e: Exception => Left(e)
    }
}
    
object State extends Enumeration
{
  val Open = Value("open")
  val Closed = Value("closed")
}

abstract class RequestIssueState
{
  override def toString =
    this match {
      case IssueState(state) => state.toString
      case All               => "all"
    }
}

case class IssueState(state: State.Value) extends RequestIssueState
case object All extends RequestIssueState

object IssueSorting extends Enumeration
{
  val Created = Value("created")
  val Updated = Value("updated")
  val Comments = Value("comments")
}

object CommentSorting extends Enumeration
{
  val Created = Value("created")
  val Updated = Value("created")
}

object Direction extends Enumeration
{
  val Asc = Value("asc")
  val Desc = Value("desc")
}

abstract class Server extends JSONable
{
  def name: String
  
  def apiURI: String
}

object Server
{
  implicit def fromJSONObject(jsonObject: JSONObject) =
    try {
      jsonObject.getString("class") match {
        case "GitHubServer" => Right(GitHubServer(jsonObject.getString("apiURI")))
      }
    } catch {
      case e: Exception => Left(e)
    }
}

case class GitHubServer(apiURI: String = "https://api.github.com") extends Server
{
  override def toJSONObject = {
    val jsonObject = new JSONObject()
    jsonObject.put("class", "GitHubServer")
    jsonObject.put("apiURI", apiURI)
  }
  
  override def name = "GitHub"
}
