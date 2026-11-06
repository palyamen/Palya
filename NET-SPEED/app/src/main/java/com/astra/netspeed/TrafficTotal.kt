package com.astra.netspeed

import android.content.Context
import android.net.TrafficStats

/**
 * Persistent device-wide received traffic counter.
 * TrafficStats resets after reboot, so a lower raw value is treated as a new boot.
 */
object TrafficTotal {
    private const val PREFS = "net_speed"
    private const val KEY_TOTAL_RX = "total_rx_bytes"
    private const val KEY_LAST_RX = "last_rx_bytes"

    @Synchronized
    fun record(context: Context): Long {
        val current = TrafficStats.getTotalRxBytes()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val total = prefs.getLong(KEY_TOTAL_RX, 0L)
        val previous = prefs.getLong(KEY_LAST_RX, -1L)

        if (current < 0L) return total

        val updated = when {
            previous < 0L -> total
            current >= previous -> total + (current - previous)
            else -> total + current // TrafficStats counter restarted after device reboot
        }

        prefs.edit()
            .putLong(KEY_TOTAL_RX, updated)
            .putLong(KEY_LAST_RX, current)
            .apply()
        return updated
    }

    @Synchronized
    fun reset(context: Context): Long {
        val current = TrafficStats.getTotalRxBytes()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TOTAL_RX, 0L)
            .putLong(KEY_LAST_RX, current)
            .apply()
        return 0L
    }
}
