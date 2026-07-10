package com.example.util

import android.os.SystemClock

object StableTime {
    @Volatile
    private var baseSystemTime: Long = System.currentTimeMillis()
    @Volatile
    private var baseElapsedRealtime: Long = SystemClock.elapsedRealtime()

    fun init() {
        baseSystemTime = System.currentTimeMillis()
        baseElapsedRealtime = SystemClock.elapsedRealtime()
    }

    fun currentTimeMillis(): Long {
        return baseSystemTime + (SystemClock.elapsedRealtime() - baseElapsedRealtime)
    }
}
