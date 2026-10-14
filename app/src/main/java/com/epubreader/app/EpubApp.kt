package com.epubreader.app

import android.app.Application
import android.webkit.WebView
import com.epubreader.app.BuildConfig

class EpubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    companion object {
        lateinit var instance: EpubApp
            private set
    }
}
