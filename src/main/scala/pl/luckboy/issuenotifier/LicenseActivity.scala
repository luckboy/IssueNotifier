/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import java.util.Scanner

class LicenseActivity extends Activity with TypedActivity
{
  override def onCreate(bundle: Bundle)
  {
    super.onCreate(bundle)
    setContentView(R.layout.license)
    findView(TR.licenseWebView).loadUrl("file:///android_asset/license.html")
  }
}
