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
	var loadingElemAbsOffsetTop = loadingElem.offsetTop || Infinity;
	//IssueNotifierObject.log("scrollBottom = " + scrollBottom);
	//IssueNotifierObject.log("loadingElemAbsOffsetTop = " + loadingElemAbsOffsetTop);
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
	var areUnloadedComments = IssueNotifierObject.areUnloadedComments();
	//IssueNotifierObject.log("areUnloadedComments = " + areUnloadedComments);
	if(!areUnloadedComments) document.getElementById("loading").style.display = "none";
	if(mustLoadComments()) IssueNotifierObject.loadComments();
}
