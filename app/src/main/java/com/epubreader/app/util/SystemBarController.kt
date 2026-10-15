package com.epubreader.app.util

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object SystemBarController {
    private const val TOP_TAG = "livre_system_bar_top"
    private const val BOTTOM_TAG = "livre_system_bar_bottom"

    fun apply(activity: Activity) {
        val window = activity.window
        val isNight = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isReader = activity.componentName.className.contains("Reader")

        val lightBars = !isNight && !isReader
        val barColor = if (isReader || isNight) {
            Color.BLACK
        } else {
            val typedValue = android.util.TypedValue()
            if (activity.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
                typedValue.data
            } else {
                Color.parseColor("#E0F0EA")
            }
        }

        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = barColor
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }

        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        if (Build.VERSION.SDK_INT < 35) return

        if (content.findViewWithTag<View>(TOP_TAG) == null) {
            content.addView(View(activity).apply {
                tag = TOP_TAG
                setBackgroundColor(barColor)
                isClickable = false
                isFocusable = false
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0,
                android.view.Gravity.TOP,
            ))
        } else {
            content.findViewWithTag<View>(TOP_TAG)?.setBackgroundColor(barColor)
        }

        if (content.findViewWithTag<View>(BOTTOM_TAG) == null) {
            content.addView(View(activity).apply {
                tag = BOTTOM_TAG
                setBackgroundColor(barColor)
                isClickable = false
                isFocusable = false
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0,
                android.view.Gravity.BOTTOM,
            ))
        } else {
            content.findViewWithTag<View>(BOTTOM_TAG)?.setBackgroundColor(barColor)
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = view.findViewWithTag<View>(TOP_TAG)
            val bottom = view.findViewWithTag<View>(BOTTOM_TAG)
            (top?.layoutParams as? FrameLayout.LayoutParams)?.also { lp ->
                lp.height = bars.top
                top.layoutParams = lp
            }
            (bottom?.layoutParams as? FrameLayout.LayoutParams)?.also { lp ->
                lp.height = bars.bottom
                bottom.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }
}
