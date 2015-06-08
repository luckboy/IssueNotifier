/****************************************************************************
 *   Copyright (C) 2015 Łukasz Szpakowski.                                  *
 *                                                                          *
 *   This software is licensed under the GNU General Public License         *
 *   v3 or later. See the LICENSE file for the full licensing terms.        *
 ****************************************************************************/
package pl.luckboy.issuenotifier
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Binder
import java.util.Date
import AndroidUtils._
import DataStorage._
import LogStringUtils._
import android.os.PowerManager

class MainService extends Service
{
  private val mTag = getClass().getSimpleName()
  
  private var mHandler: Handler = null
  private var mLastReposTimestampInfos: Map[Repository, RepositoryTimestampInfo] = null
  private var mStopFlag: StopFlag = null
  private var mSettings: Settings = null
  private var mHasNotification = false
  
  private val mBinder = new Binder {
    def getService(): MainService = MainService.this
  }
  
  override def onBind(ident: Intent) = mBinder
  
  override def onCreate()
  {
    mHandler = new Handler()
    mLastReposTimestampInfos = null
    mStopFlag = null
    mSettings = null
    mHasNotification = false 
  }
  
  private def fetchAndNotify(stopFlag: StopFlag)()
  {
    val startTime = System.currentTimeMillis()
    val tmpReposes = log(mTag, loadRepositories(this)).fold(_ => Vector(), identity)
    val tmpLastReposTimestampInfos = log(mTag, loadLastRepositoryTimestampInfos(this)).fold(_ => Map[Repository, RepositoryTimestampInfo](), identity)
    val tmpOldReposTimestampInfos = log(mTag, loadOldRepositoryTimestampInfos(this)).fold(_ => Map[Repository, RepositoryTimestampInfo](), identity)
    val tmpInterval = mSettings.interval
    val tmpState = mSettings.state
    val tmpSortingByCreated = mSettings.sortingByCreated
    val tmpSorting = if(mSettings.sortingByCreated) IssueSorting.Created else IssueSorting.Updated
    log(mTag, "fetchAndNotify(): tmpInverval = " + tmpInterval)
    log(mTag, "fetchAndNotify(): tmpState = " + tmpState)
    log(mTag, "fetchAndNotify(): tmpSortingByCreated = " + tmpSortingByCreated)
    log(mTag, "fetchAndNotify(): tmpSorting = " + tmpSorting)
    for(p <- tmpReposes.zipWithIndex)
      log(mTag, "fetchAndNotify(): tmpReposes(" + p._2 + ") = " +  stringFromRepository(p._1))
    for(p <- tmpLastReposTimestampInfos)
      log(mTag, "fetchAndNotify(): LRTIs(" + stringFromRepository(p._1) + ") = " + stringFromRepositoryTimestampInfo(p._2))
    for(p <- tmpOldReposTimestampInfos)
      log(mTag, "fetchAndNotify(): ORTIs(" + stringFromRepository(p._1) + ") = " + stringFromRepositoryTimestampInfo(p._2))
    startThreadAndPost(mHandler, stopFlag) {
      () =>
        val issueInfoLists = tmpReposes.map {
          repos =>
            val since = tmpOldReposTimestampInfos.get(repos).map {
              reposTimestampInfo =>
                if(!tmpSortingByCreated) reposTimestampInfo.updatedIssueAt else reposTimestampInfo.createdIssueAt
            }
            MainService.DataFetchers.get(repos.server).map {
              dataFetcher =>
                log(mTag, "fetchAndNotify(): fetching issues from " + stringFromRepository(repos) + " ...")
                log(mTag, "fetchAndNotify(): since = " + since)
                val res = dataFetcher.fetchIssueInfos(
                    repos, Some(tmpState), Some(tmpSorting), Some(Direction.Desc), since, 
                    Some(1), Some(2), Some(5000))
                log(mTag, "fetchAndNotify(): fetched issues from " + stringFromRepository(repos) +
                    res.fold(_ => "", issueInfos => " (issueInfoCount = " + issueInfos.size + ")"))
                res
            }.getOrElse(Right(Vector())) match {
              case left @ Left(_)    => log(mTag, left); repos -> Vector()
              case Right(issueInfos) =>
                val newIssueInfos = since.map {
                  date =>
                    issueInfos.filter {
                      issueInfo =>
                        if(!tmpSortingByCreated)
                          issueInfo.updatedAt.compareTo(date) > 0
                        else
                          issueInfo.createdAt.compareTo(date) > 0
                    }
                }.getOrElse(issueInfos)
                repos -> newIssueInfos
            }
        }
        val reposTimestampInfos = issueInfoLists.map {
          case (repos, issueInfos) => 
            val tmpCreatedIssueAt = tmpLastReposTimestampInfos.get(repos).map {
              reposTimestampInfo => reposTimestampInfo.createdIssueAt
            }.getOrElse(new Date(0))
            val createdIssueAt = issueInfos.foldLeft(tmpCreatedIssueAt) {
              case (date, issueInfo) => if(date.compareTo(issueInfo.createdAt) > 0) date else issueInfo.createdAt
            }
            val tmpUpdatedIssueAt = tmpLastReposTimestampInfos.get(repos).map {
              reposTimestampInfo => reposTimestampInfo.updatedIssueAt
            }.getOrElse(new Date(0))
            val updatedIssueAt = if(!tmpSortingByCreated)
              issueInfos.foldLeft(tmpUpdatedIssueAt) {
                case (date, issueInfo) => if(date.compareTo(issueInfo.updatedAt) > 0) date else issueInfo.updatedAt
              }
            else
              tmpUpdatedIssueAt
            repos -> RepositoryTimestampInfo(createdIssueAt, updatedIssueAt)
        }.toMap
        val mustNotify = issueInfoLists.exists { !_._2.isEmpty }
        val issueCount = issueInfoLists.foldLeft(0) { _ + _._2.size }
        (reposTimestampInfos, mustNotify, issueCount)
    } {
      case (reposTimestampInfos, mustNotify, issueCount) =>
        mLastReposTimestampInfos = reposTimestampInfos
        for(p <- mLastReposTimestampInfos)
          log(mTag, "fetchAndNotify(): newLRTIs(" + stringFromRepository(p._1) + ") = " + stringFromRepositoryTimestampInfo(p._2))
        log(mTag, "fetchAndNotify(): mustNotify = " + mustNotify)
        log(mTag, "fetchAndNotify(): issueCount = " + issueCount)
        log(mTag, storeLastRepositoryTimestampInfos(this, mLastReposTimestampInfos))
        if(mustNotify) {
          createAndAcquireNotificationWakeLock()
          val title = getResources().getQuantityString(R.plurals.notification_issues_title, issueCount)
          val msg = getResources().getQuantityString(R.plurals.notification_issues_message, issueCount)
          val intent = new Intent(MainReceiver.ActionIssuePairs)
          log(mTag, "fetchAndNotify(): intent.getAction() = " + intent.getAction())
          val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
          log(mTag, "fetchAndNotify(): notify(..., " + title + ", " + msg + ", ...)")
          AndroidUtils.notify(this, 1, R.drawable.small_app_icon, Some(R.drawable.app_icon), title, msg, Some(pendingIntent), true, true, mSettings.ringtone, mSettings.vibration)
        }
        val alarmManager = getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
        val intent2 = new Intent(this, classOf[AlarmReceiver])
        val pendingIntent2 = PendingIntent.getBroadcast(this, 0, intent2, 0)
        log(mTag, "fetchAndNotify(): intent2.getAction() = " + intent2.getAction())
        val endTime = System.currentTimeMillis()
        val timeDiff = endTime - startTime
        val delay = if(tmpInterval - timeDiff > 1000) tmpInterval - timeDiff else 1000
        log(mTag, "fetchAndNotify(): startTime = " + new Date(startTime))
        log(mTag, "fetchAndNotify(): endTime = " + new Date(endTime))
        log(mTag, "fetchAndNotify(): delay = " + delay)
        log(mTag, "fetchAndNotify(): endTime + delay = " + new Date(endTime + delay))
        alarmManager.set(AlarmManager.RTC_WAKEUP, endTime + delay, pendingIntent2)
        AlarmReceiver.releaseWakeLock()
    }
  }
  
  override def onStartCommand(intent: Intent, flags: Int, startId: Int) = {
    log(mTag, "onStartCommand(): starting ...")
    if(mLastReposTimestampInfos == null) mLastReposTimestampInfos = log(mTag, loadLastRepositoryTimestampInfos(this)).fold(_ => Map(), identity)
    if(mStopFlag == null) mStopFlag = StopFlag(false)
    if(mSettings == null) mSettings = Settings(this)
    if(!mHasNotification) {
      val intent = new Intent(this, classOf[MainActivity])
      val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)    
      val title = getResources().getString(R.string.notification_service_title)
      val msg = getResources().getString(R.string.notification_service_message)
      log(mTag, "onStartCommand(): notify(..., " + title + ", " + msg + ", ...)")
      AndroidUtils.notify(this, 0, R.drawable.small_service_icon, Some(R.drawable.service_icon), title, msg, Some(pendingIntent), false, false, false, false)
      mHasNotification = true;
    }
    log(mTag, "onStartCommand(): started")
    AlarmReceiver.createAndAcquireWakeLock(this)
    fetchAndNotify(mStopFlag)
    Service.START_STICKY
  }
  
  override def onDestroy()
  {
    log(mTag, "onDestroy(): stopping ...")
    AlarmReceiver.releaseWakeLock()
    cancelNotification(this, 0)
    if(mStopFlag != null) mStopFlag.b = true
    if(mHandler != null) mHandler.removeCallbacksAndMessages(null)
    mHasNotification = false
    mSettings = null
    mStopFlag = null
    mLastReposTimestampInfos = null
    mHandler = null
    val alarmManager = getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    val intent = new Intent(this, classOf[AlarmReceiver])
    val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
    alarmManager.cancel(pendingIntent)
    log(mTag, "onDestroy(): stopped")
  }
  
  private def createAndAcquireNotificationWakeLock()
  {
    val powerManager = getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    val notificatinWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MainServiceWakeLock")
    notificatinWakeLock.acquire(20000)
  }
}

object MainService
{
  val DataFetchers = Map[Server, DataFetcher](GitHubServer() -> DataFetcher(GitHubServer()))
}
