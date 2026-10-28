package com.epubreader.app.ui

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.epubreader.app.MainActivity
import com.epubreader.app.R
import com.epubreader.app.data.AppDatabase
import com.epubreader.app.data.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsViewController(
    private val activity: MainActivity,
    private val prefs: PrefsManager,
    private val onOpenGoalSettings: () -> Unit
) {

    private data class StatsValues(
        val weekSeconds: Int,
        val yearSeconds: Int,
        val todaySeconds: Int,
        val chapters: Int,
        val pages: Int,
        val finished: Int,
        val streak: Int,
        val todayPages: Int
    )

    fun show(statsRoot: View) {
        statsRoot.findViewById<View>(R.id.cardStatsGoal)?.setOnClickListener { onOpenGoalSettings() }
        val now = System.currentTimeMillis()
        val weekStart = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.timeInMillis
        val yearStart = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        activity.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(activity.applicationContext)
                val dao = db.readingSessionDao()
                val days = dao.activeDays()
                var streak = 0
                if (days.isNotEmpty()) {
                    val keys = days.toHashSet()
                    val cursor = Calendar.getInstance().apply {
                        timeInMillis = now
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }
                    while (true) {
                        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cursor.time)
                        if (!keys.contains(key)) break
                        streak++
                        cursor.add(Calendar.DAY_OF_YEAR, -1)
                    }
                }
                StatsValues(
                    weekSeconds = dao.activeSecondsSince(weekStart),
                    yearSeconds = dao.activeSecondsSinceYear(yearStart),
                    todaySeconds = dao.activeSecondsSince(todayStart),
                    chapters = dao.chaptersSince(yearStart),
                    pages = dao.pagesSince(yearStart),
                    finished = db.bookDao().getFinishedCount(),
                    streak = streak,
                    todayPages = dao.pagesSince(todayStart)
                )
            }
            val isGoalEnabled = prefs.showGoalOnDashboard
            val goalFractionTv = statsRoot.findViewById<TextView>(R.id.statsGoalFraction)
            val goalProgressRow = statsRoot.findViewById<View>(R.id.statsGoalProgressRow)
            val goalDisabledInfo = statsRoot.findViewById<TextView>(R.id.statsGoalDisabledInfo)

            if (isGoalEnabled) {
                goalFractionTv?.visibility = View.VISIBLE
                goalProgressRow?.visibility = View.VISIBLE
                goalDisabledInfo?.visibility = View.GONE

                val goalType = prefs.goalType
                if (goalType == "pages") {
                    val todayPages = stats.todayPages
                    val goalPages = prefs.dailyGoalPages.coerceAtLeast(1)
                    val percent = ((todayPages * 100) / goalPages).coerceIn(0, 100)
                    goalFractionTv?.text = activity.getString(R.string.stats_goal_fraction_pages, todayPages, goalPages)
                    statsRoot.findViewById<ProgressBar>(R.id.statsGoalProgressBar)?.progress = percent
                    statsRoot.findViewById<TextView>(R.id.statsGoalPercent)?.text = "$percent%"
                } else {
                    val todayMinutes = stats.todaySeconds / 60
                    val goalMinutes = prefs.dailyGoalMinutes.coerceAtLeast(1)
                    val percent = ((todayMinutes * 100) / goalMinutes).coerceIn(0, 100)
                    goalFractionTv?.text = activity.getString(R.string.stats_goal_fraction_minutes, todayMinutes, goalMinutes)
                    statsRoot.findViewById<ProgressBar>(R.id.statsGoalProgressBar)?.progress = percent
                    statsRoot.findViewById<TextView>(R.id.statsGoalPercent)?.text = "$percent%"
                }
            } else {
                goalFractionTv?.visibility = View.GONE
                goalProgressRow?.visibility = View.GONE
                goalDisabledInfo?.visibility = View.VISIBLE
            }

            statsRoot.findViewById<TextView>(R.id.statsWeek)?.text = formatStatsMinutes(stats.weekSeconds)
            statsRoot.findViewById<TextView>(R.id.statsYear)?.text = formatStatsMinutes(stats.yearSeconds)
            statsRoot.findViewById<TextView>(R.id.statsFinished)?.text = stats.finished.toString()
            statsRoot.findViewById<TextView>(R.id.statsStreak)?.text =
                "${stats.streak} ${if (stats.streak == 1) "day" else activity.getString(R.string.stats_days)}"
            statsRoot.findViewById<TextView>(R.id.statsChapters)?.text = stats.chapters.toString()
            statsRoot.findViewById<TextView>(R.id.statsPages)?.text = stats.pages.toString()
        }
    }

    private fun formatStatsMinutes(seconds: Int): String {
        val minutes = seconds / 60
        return if (minutes < 60) activity.getString(R.string.stats_minutes_value, minutes)
        else activity.getString(R.string.stats_hours_value, minutes / 60, minutes % 60)
    }
}
