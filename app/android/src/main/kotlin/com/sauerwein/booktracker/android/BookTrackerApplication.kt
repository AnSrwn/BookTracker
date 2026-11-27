package com.sauerwein.booktracker.android

import android.app.Application
import com.sauerwein.booktracker.app.initKoin

class BookTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(flavorId = BuildConfig.FLAVOR)
    }
}
