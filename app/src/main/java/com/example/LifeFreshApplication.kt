package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.ai.chat.config.AIConfig
import com.example.data.security.AIQuotaManager
import com.google.android.play.core.splitcompat.SplitCompat

/**
 * Custom Application class for LifeFresh QuickNote Pro.
 * Integrates Google Play SplitCompat to enable on-demand language splits.
 */
open class LifeFreshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AIConfig.customGeminiApiKeyProvider = {
            AIQuotaManager.getCustomGeminiKey(this) ?: ""
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            SplitCompat.install(this)
        } catch (e: Throwable) {
            Log.w("LifeFreshApplication", "Failed to install SplitCompat in Application", e)
        }
    }

    companion object {
        lateinit var instance: LifeFreshApplication
            private set
    }
}
