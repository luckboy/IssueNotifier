/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import java.util.Date

abstract class DataFetcher
{
  def defaultPerPage: Int
  
  def fetchIssue(repos: Repository, issueInfo: IssueInfo, timeout: Option[Int]): Either[Exception, Issue]
  
  def fetchIssueInfos(repos: Repository, state: Option[State.Value], sorting: Option[IssueSorting.Value], dir: Option[Direction.Value], since: Option[Date], page: Option[Long], perPage: Option[Long], timeout: Option[Int]): Either[Exception, Vector[IssueInfo]]
  
  def fetchComments(repos: Repository, issueInfo: IssueInfo, sorting: Option[CommentSorting.Value], dir: Option[Direction.Value], since: Option[Date], page: Option[Long], perPage: Option[Long], timeout: Option[Int]): Either[Exception, Vector[Comment]]
}

object DataFetcher
{
  def apply(server: Server): DataFetcher =
    server match {
      case GitHubServer(apiURI) => new GitHubDataFetcher(apiURI)
    }
}
