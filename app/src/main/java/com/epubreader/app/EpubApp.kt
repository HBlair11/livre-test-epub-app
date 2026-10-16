package com.epubreader.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class EpubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Locks the entire application to Dark Mode regardless of the system phone setting.
        // Prevents the system from applying broken daytime resources.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    companion object {
        lateinit var instance: EpubApp
            private set
    }
}
