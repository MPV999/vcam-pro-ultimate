package com.vcampro.ultimate

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * VCAM PRO ULTIMATE VIP 2026 — Application.
 *
 * ỨNG DỤNG LOCAL-FIRST: không có mã khởi tạo mạng, không có SDK cloud,
 * không có analytics. Không khai báo quyền INTERNET trong manifest.
 */
@HiltAndroidApp
class VcamApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
    }
}
