package com.grokplayer.tv.data

import android.os.SystemClock
import android.util.Log

internal object Perf {
    fun <T> measure(name: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            Log.i("GrokPlayer", "perf $name ${SystemClock.elapsedRealtime() - start}ms")
        }
    }
}
