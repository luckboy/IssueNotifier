/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/

var loadingAngle = 225.0;

function onDrawLoading() {
	var loadingElem = document.getElementById("loading");
	var r = 16.0;
	var x1 = 24.0 + Math.cos((2.0 * loadingAngle * Math.PI) / 360.0) * r;
	var y1 = 24.0 - Math.sin((2.0 * loadingAngle * Math.PI) / 360.0) * r;
	var x2 = 24.0 + Math.cos((2.0 * (loadingAngle - 225.0) * Math.PI) / 360.0) * r;
	var y2 = 24.0 - Math.sin((2.0 * (loadingAngle - 225.0) * Math.PI) / 360.0) * r;
	loadingElem.innerHTML = (
			'<svg width="36pt" height="36pt" viewBox="0 0 48 48">\n' +
			'<path d="M' + x1 + ',' + y1 + ' A' + r + ',' + r + ' 0 1 1 ' + x2 + ',' + y2 + '" fill="transparent" stroke="#e6e6e6" stroke-width="3"/>\n' +
			'</svg>');
	loadingAngle = (loadingAngle + 5.0) % 360.0;
}
