package com.jake.duolauncher

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner

/**
 * Integration tests were written for Home with Discover on its right swipe, which is now off by default.
 * Turn it on before the app starts so they exercise the same Home.
 */
class DuoTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        app.getSharedPreferences("home_gestures", Application.MODE_PRIVATE).edit().putBoolean("discoverSwipe", true).commit()
        super.callApplicationOnCreate(app)
    }
}
