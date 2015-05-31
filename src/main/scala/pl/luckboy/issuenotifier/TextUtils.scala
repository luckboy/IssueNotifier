/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.GregorianCalendar

object TextUtils
{
  private val dateSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
  
  private val timeSimpleDateFormat = new SimpleDateFormat("HH:mm:ss")
  
  private val longSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  
  def textFromRepository(repos: Repository) = repos.userName + "/" + repos.name
  
  def textFromDate(date: Date, currentDate: Date) = {
    val calendar = new GregorianCalendar()
    calendar.setTime(date)
    val currentCalendar = new GregorianCalendar()
    currentCalendar.setTime(currentDate)
    if(calendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == currentCalendar.get(Calendar.DAY_OF_YEAR))
      timeSimpleDateFormat.format(date)
    else
      dateSimpleDateFormat.format(date)
  }
  
  def textFromDateForLongFormat(date: Date) = longSimpleDateFormat.format(date)
}
