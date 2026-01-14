package com.greenmobilitypass.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver to restart the TripDetectionService after device boot.
 * Only restarts if detection was previously enabled by the user.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "GreenMobilityPassPrefs"
        private const val KEY_DETECTION_ENABLED = "detection_enabled"

        fun setDetectionEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DETECTION_ENABLED, enabled)
                .apply()
        }

        fun isDetectionEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DETECTION_ENABLED, false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed received")

            if (isDetectionEnabled(context)) {
                Log.d(TAG, "Detection was enabled, restarting service")
                startDetectionService(context)
            } else {
                Log.d(TAG, "Detection was not enabled, not starting service")
            }
        }
    }

    private fun startDetectionService(context: Context) {
        val serviceIntent = Intent(context, TripDetectionService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Detection service started after boot")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start detection service after boot", e)
        }
    }
}
