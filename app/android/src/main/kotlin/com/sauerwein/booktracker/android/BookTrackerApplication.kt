package com.sauerwein.booktracker.android

import android.app.Application
import com.sauerwein.booktracker.app.di.initKoin

class BookTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(flavorId = BuildConfig.FLAVOR)
    }
}
