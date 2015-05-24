/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier

object LogStringUtils
{
  def stringFromRepository(repos: Repository) =
    repos.server.name + ":" + repos.userName + "/" + repos.name
  
  def stringFromRepositoryAndIssueInfo(repos: Repository, issueInfo: IssueInfo) =
    stringFromRepository(repos) + "/" + issueInfo.number
  
  def stringFromRepositoryTimestampInfo(reposTimestampInfo: RepositoryTimestampInfo) =
    "RTI(" + reposTimestampInfo.createdIssueAt + ", " + reposTimestampInfo.updatedIssueAt + ")"
}
