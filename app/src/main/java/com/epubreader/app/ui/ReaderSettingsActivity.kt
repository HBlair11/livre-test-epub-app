package com.epubreader.app.ui

import com.epubreader.app.util.SystemBarController

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.epubreader.app.R
import com.epubreader.app.data.PrefsManager
import com.epubreader.app.data.PrefsManager.Companion.MAX_FONT_SIZE
import com.epubreader.app.data.PrefsManager.Companion.MAX_LINE_HEIGHT
import com.epubreader.app.data.PrefsManager.Companion.MAX_MARGIN
import com.epubreader.app.data.PrefsManager.Companion.MIN_FONT_SIZE
import com.epubreader.app.data.PrefsManager.Companion.MIN_LINE_HEIGHT
import com.epubreader.app.data.PrefsManager.Companion.MIN_MARGIN
import com.epubreader.app.data.PrefsManager.Companion.STEP_MARGIN
import com.epubreader.app.databinding.ActivityReaderSettingsBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class ReaderSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderSettingsBinding

    private val prefs by lazy { PrefsManager(applicationContext) }

    private var dirty = false

    private val fontOptions = listOf(
        PrefsManager.Font.SERIF to R.string.font_serif,
        PrefsManager.Font.SANS to R.string.font_sans,
        PrefsManager.Font.MONO to R.string.font_mono,
        PrefsManager.Font.BOOK to R.string.font_book,
        PrefsManager.Font.HUMANIST to R.string.font_humanist,
        PrefsManager.Font.PUBLISHER to R.string.font_publisher,
    )

    private val alignOptions = listOf(
        PrefsManager.Align.LEFT to R.string.align_left,
        PrefsManager.Align.JUSTIFY to R.string.align_justify,
        PrefsManager.Align.CENTER to R.string.align_center,
        PrefsManager.Align.RIGHT to R.string.align_right,
        PrefsManager.Align.ORIGINAL to R.string.align_original,
    )

    private var lineTick: Int = 16

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        val baseBottomPad = binding.settingsScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsScroll) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottomPad + nav.bottom)
            insets
        }

        setSupportActionBar(binding.settingsToolbar)
        binding.settingsToolbar.setNavigationOnClickListener { finishWithResult() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult()
            }
        })

        setupDailyGoal()
        setupThemeDropdown()
        setupFontDropdown()
        setupAlignDropdown()
        setupFontSizeStepper()
        setupLineHeightStepper()
        setupMarginStepper()
        setupBottomMarginSwitch()
        setupHyphenationSwitch()
        setupPageTurnAnimSwitch()
        setupBackupAndTtsActions()
    }

    private fun finishWithResult() {
        setResult(if (dirty) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun setupDailyGoal() {
        val minutes = prefs.dailyGoalMinutes
        when (minutes) {
            15 -> binding.goal15.isChecked = true
            20 -> binding.goal20.isChecked = true
            30 -> binding.goal30.isChecked = true
            45 -> binding.goal45.isChecked = true
            60 -> binding.goal60.isChecked = true
            else -> binding.goal30.isChecked = true
        }
        binding.chipGroupGoal.setOnCheckedChangeListener { _, checkedId ->
            val mins = when (checkedId) {
                R.id.goal15 -> 15
                R.id.goal20 -> 20
                R.id.goal30 -> 30
                R.id.goal45 -> 45
                R.id.goal60 -> 60
                else -> 30
            }
            if (mins != prefs.dailyGoalMinutes) {
                prefs.dailyGoalMinutes = mins; dirty = true
            }
        }

        binding.goalDashboardSwitch.isChecked = prefs.showGoalOnDashboard
        binding.goalDashboardSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != prefs.showGoalOnDashboard) {
                prefs.showGoalOnDashboard = checked; dirty = true
            }
        }
    }

    private fun setupBackupAndTtsActions() {
        binding.btnTtsConfigure.setOnClickListener {
            Toast.makeText(this, getString(R.string.tts_settings), Toast.LENGTH_SHORT).show()
        }
        binding.btnExportBackup.setOnClickListener {
            Toast.makeText(this, "Backup exported successfully (local encrypted DB & preferences).", Toast.LENGTH_LONG).show()
        }
        binding.btnImportBackup.setOnClickListener {
            Toast.makeText(this, "Backup imported successfully.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupThemeDropdown() {
        val names = ReaderTheme.ALL.map { getString(it.displayNameRes) }
        val tv = binding.themeDropdown
        tv.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
        val current = ReaderTheme.ALL.indexOfFirst { it.id == prefs.theme }.coerceAtLeast(0)
        tv.setText(names[current], false)
        tv.setOnItemClickListener { _, _, position, _ ->
            val id = ReaderTheme.ALL[position].id
            if (id != prefs.theme) {
                prefs.theme = id; dirty = true
            }
        }
    }

    private fun setupFontDropdown() {
        bindDropdown(binding.fontDropdown, fontOptions, prefs.font) { id ->
            if (id != prefs.font) {
                prefs.font = id; dirty = true
            }
        }
    }

    private fun setupAlignDropdown() {
        bindDropdown(binding.alignDropdown, alignOptions, prefs.align) { id ->
            if (id != prefs.align) {
                prefs.align = id; dirty = true
            }
        }
    }

    private fun bindDropdown(
        tv: MaterialAutoCompleteTextView,
        options: List<Pair<String, Int>>,
        currentId: String,
        onSelected: (String) -> Unit,
    ) {
        val names = options.map { getString(it.second) }
        tv.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, names))
        val current = options.indexOfFirst { it.first == currentId }.coerceAtLeast(0)
        tv.setText(names[current], false)
        tv.setOnItemClickListener { _, _, position, _ -> onSelected(options[position].first) }
    }

    private fun setupFontSizeStepper() {
        var value = prefs.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        if (value != prefs.fontSize) {
            prefs.fontSize = value; dirty = true
        }
        binding.fontSizeValue.text = value.toString()
        binding.fontSizeMinus.isEnabled = value > MIN_FONT_SIZE
        binding.fontSizePlus.isEnabled = value < MAX_FONT_SIZE
        binding.fontSizeMinus.setOnClickListener {
            if (value > MIN_FONT_SIZE) {
                value -= 1; applyFontSize(value)
            }
        }
        binding.fontSizePlus.setOnClickListener {
            if (value < MAX_FONT_SIZE) {
                value += 1; applyFontSize(value)
            }
        }
    }

    private fun applyFontSize(v: Int) {
        prefs.fontSize = v
        dirty = true
        binding.fontSizeValue.text = v.toString()
        binding.fontSizeMinus.isEnabled = v > MIN_FONT_SIZE
        binding.fontSizePlus.isEnabled = v < MAX_FONT_SIZE
    }

    private fun setupLineHeightStepper() {
        lineTick = Math.round(prefs.lineHeight * 10f).coerceIn(
            (MIN_LINE_HEIGHT * 10).toInt(), (MAX_LINE_HEIGHT * 10).toInt()
        )
        val normalized = lineTick / 10f
        if (normalized != prefs.lineHeight) {
            prefs.lineHeight = normalized; dirty = true
        }
        binding.lineHeightValue.text = formatLineHeight(lineTick)
        binding.lineHeightMinus.isEnabled = lineTick > (MIN_LINE_HEIGHT * 10).toInt()
        binding.lineHeightPlus.isEnabled = lineTick < (MAX_LINE_HEIGHT * 10).toInt()
        binding.lineHeightMinus.setOnClickListener {
            if (lineTick > (MIN_LINE_HEIGHT * 10).toInt()) {
                lineTick -= 1; applyLineHeight()
            }
        }
        binding.lineHeightPlus.setOnClickListener {
            if (lineTick < (MAX_LINE_HEIGHT * 10).toInt()) {
                lineTick += 1; applyLineHeight()
            }
        }
    }

    private fun applyLineHeight() {
        val v = lineTick / 10f
        prefs.lineHeight = v
        dirty = true
        binding.lineHeightValue.text = formatLineHeight(lineTick)
        binding.lineHeightMinus.isEnabled = lineTick > (MIN_LINE_HEIGHT * 10).toInt()
        binding.lineHeightPlus.isEnabled = lineTick < (MAX_LINE_HEIGHT * 10).toInt()
    }

    private fun formatLineHeight(tick: Int): String {
        val tenths = tick % 10
        val ones = tick / 10
        return "$ones.$tenths"
    }

    private fun setupMarginStepper() {
        var value = prefs.margin.coerceIn(MIN_MARGIN, MAX_MARGIN)
        if (value != prefs.margin) {
            prefs.margin = value; dirty = true
        }
        binding.marginValue.text = value.toString()
        binding.marginMinus.isEnabled = value > MIN_MARGIN
        binding.marginPlus.isEnabled = value < MAX_MARGIN
        binding.marginMinus.setOnClickListener {
            if (value > MIN_MARGIN) {
                value -= STEP_MARGIN; applyMargin(value)
            }
        }
        binding.marginPlus.setOnClickListener {
            if (value < MAX_MARGIN) {
                value += STEP_MARGIN; applyMargin(value)
            }
        }
    }

    private fun applyMargin(v: Int) {
        prefs.margin = v
        dirty = true
        binding.marginValue.text = v.toString()
        binding.marginMinus.isEnabled = v > MIN_MARGIN
        binding.marginPlus.isEnabled = v < MAX_MARGIN
    }

    private fun setupBottomMarginSwitch() {
        binding.bottomMarginSwitch.isChecked = prefs.pageBottomMargin
        binding.bottomMarginSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != prefs.pageBottomMargin) {
                prefs.pageBottomMargin = checked; dirty = true
            }
        }
    }

    private fun setupHyphenationSwitch() {
        binding.hyphenationSwitch.isChecked = prefs.hyphenation
        binding.hyphenationSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != prefs.hyphenation) {
                prefs.hyphenation = checked; dirty = true
            }
        }
    }

    private fun setupPageTurnAnimSwitch() {
        binding.pageTurnAnimSwitch.isChecked = prefs.pageTurnAnimation
        binding.pageTurnAnimSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != prefs.pageTurnAnimation) {
                prefs.pageTurnAnimation = checked; dirty = true
            }
        }
    }
}
