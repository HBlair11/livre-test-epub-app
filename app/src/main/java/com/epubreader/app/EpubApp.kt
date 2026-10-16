package com.epubreader.app

import android.app.Application
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import com.epubreader.app.BuildConfig

class EpubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    companion object {
        lateinit var instance: EpubApp
            private set
    }
}
