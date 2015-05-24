/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/

function mustLoadComments()
{
	var scrollBottom = window.pageYOffset + window.innerHeight;
	var loadingElem = document.getElementById("loading");
	var loadingElemAbsOffsetTop = loadingElem.offsetTop;
	return IssueNotifierObject.areUnloadedComments() && scrollBottom > loadingElemAbsOffsetTop;
}

function onScroll()
{
	if(mustLoadComments()) IssueNotifierObject.loadComments();
}

function onLoadComments()
{
	var commentListElem = document.getElementById("comments");
	var commentListHtml = IssueNotifierObject.getCommentListHtml();
	commentListElem.innerHTML += commentListHtml;
	if(mustLoadComments())
		IssueNotifierObject.loadComments();
	else
		document.getElementById("loading").style.display = "none";
}
