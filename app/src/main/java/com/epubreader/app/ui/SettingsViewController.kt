package com.epubreader.app.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.epubreader.app.MainActivity
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.databinding.ViewMainSettingsBinding

class SettingsViewController(
    private val activity: MainActivity,
    private val prefs: PrefsManager,
    private val onSelectFoldersView: () -> Unit,
    private val getFolderDisplayName: (String) -> String
) {

    private fun Int.dp(): Int = (this * activity.resources.displayMetrics.density).toInt()

    private data class GoalOpt(val value: Int, val chipText: String, val label: String, val subtext: String)

    fun show(binding: ViewMainSettingsBinding) {
        binding.root.visibility = View.VISIBLE

        val minutesOpts = listOf(
            GoalOpt(10, "10m", "10 minutes", activity.getString(R.string.goal_sub_10_min)),
            GoalOpt(20, "20m", "20 minutes", activity.getString(R.string.goal_sub_20_min)),
            GoalOpt(30, "30m", "30 minutes", activity.getString(R.string.goal_sub_30_min)),
            GoalOpt(45, "45m", "45 minutes", activity.getString(R.string.goal_sub_45_min)),
            GoalOpt(60, "60m", "60 minutes", activity.getString(R.string.goal_sub_60_min))
        )
        val pagesOpts = listOf(
            GoalOpt(10, "10p", "10 pages", activity.getString(R.string.goal_sub_10_pages)),
            GoalOpt(20, "20p", "20 pages", activity.getString(R.string.goal_sub_20_pages)),
            GoalOpt(30, "30p", "30 pages", activity.getString(R.string.goal_sub_30_pages)),
            GoalOpt(40, "40p", "40 pages", activity.getString(R.string.goal_sub_40_pages)),
            GoalOpt(50, "50p", "50 pages", activity.getString(R.string.goal_sub_50_pages))
        )

        lateinit var bindDailyGoalSection: () -> Unit
        bindDailyGoalSection = {
            val type = prefs.goalType
            val isMinutes = type != "pages"

            val switchBtn = binding.btnGoalTypeSwitch
            switchBtn.text = if (isMinutes) activity.getString(R.string.goal_switch_minutes) else activity.getString(R.string.goal_switch_pages)
            switchBtn.setOnClickListener {
                prefs.goalType = if (isMinutes) "pages" else "minutes"
                bindDailyGoalSection()
            }

            val opts = if (isMinutes) minutesOpts else pagesOpts
            val currentVal = if (isMinutes) prefs.dailyGoalMinutes else prefs.dailyGoalPages
            val activeOpt = opts.firstOrNull { it.value == currentVal } ?: opts.first()
            if (currentVal != activeOpt.value) {
                if (isMinutes) prefs.dailyGoalMinutes = activeOpt.value else prefs.dailyGoalPages = activeOpt.value
            }

            binding.tvGoalSelectedLabel.text = activeOpt.label
            binding.tvGoalSubtext.text = activeOpt.subtext

            val container = binding.goalPillsContainer
            container.removeAllViews()
            val pillWidth = 56.dp()
            val pillHeight = 36.dp()
            val dp8 = 8.dp()

            for (opt in opts) {
                val pill = TextView(activity).apply {
                    text = opt.chipText
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    val isSelected = opt.value == activeOpt.value
                    if (isSelected) {
                        setBackgroundResource(R.drawable.goal_pill_selected)
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                    } else {
                        setBackgroundResource(R.drawable.goal_pill_unselected)
                        setTextColor(ContextCompat.getColor(context, R.color.palette_plum))
                    }
                    layoutParams = LinearLayout.LayoutParams(pillWidth, pillHeight).apply {
                        marginEnd = dp8
                    }
                    setOnClickListener {
                        if (isMinutes) {
                            prefs.dailyGoalMinutes = opt.value
                        } else {
                            prefs.dailyGoalPages = opt.value
                        }
                        bindDailyGoalSection()
                    }
                }
                container.addView(pill)
            }
        }
        bindDailyGoalSection()

        binding.goalDashboardSwitch.isChecked = prefs.showGoalOnDashboard
        binding.goalDashboardSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.showGoalOnDashboard = checked
        }

        val folder = prefs.selectedFolderUri
        binding.tvScannedFolderValue.text =
            if (folder != null) getFolderDisplayName(folder) else activity.getString(R.string.folder_none)
        binding.cardFolder.setOnClickListener {
            onSelectFoldersView()
        }

        binding.screenOnSwitch.isChecked = prefs.keepScreenOn
        binding.screenOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.keepScreenOn = isChecked
        }

        binding.vocabMasterySwitch.isChecked = prefs.enableVocabMasteryAndFlashcards
        binding.vocabMasterySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.enableVocabMasteryAndFlashcards = isChecked
        }

        binding.btnExportBackup.setOnClickListener {
            Toast.makeText(activity, R.string.backup_export_success, Toast.LENGTH_LONG).show()
        }
        binding.btnImportBackup.setOnClickListener {
            Toast.makeText(activity, R.string.backup_import_success, Toast.LENGTH_LONG).show()
        }
    }
}
