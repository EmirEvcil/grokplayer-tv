package com.grokplayer.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/** Starts a fresh app process after restore. Runs outside the main process so it can relaunch it. */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(EXTRA_PID, -1)
        if (mainPid > 0 && mainPid != Process.myPid()) {
            Process.killProcess(mainPid)
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finish()
    }

    companion object {
        const val EXTRA_PID = "main_pid"
    }
}
