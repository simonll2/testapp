package com.greenmobilitypass.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * BroadcastReceiver for Activity Recognition updates.
 * Receives activity detection results from Google Play Services and
 * forwards them to the TripDetectionService.
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityRecognition"
        const val ACTION_ACTIVITY_UPDATE = "com.greenmobilitypass.ACTION_ACTIVITY_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            result?.let { handleActivityResult(context, it) }
        }
    }

    private fun handleActivityResult(context: Context, result: ActivityRecognitionResult) {
        // Get the most probable activity
        val mostProbableActivity = result.mostProbableActivity

        Log.d(TAG, "Activity detected: ${getActivityName(mostProbableActivity.type)}, " +
                "confidence: ${mostProbableActivity.confidence}")

        // Forward to service via local broadcast
        val serviceIntent = Intent(ACTION_ACTIVITY_UPDATE).apply {
            setPackage(context.packageName)
            putExtra("activity_type", mostProbableActivity.type)
            putExtra("confidence", mostProbableActivity.confidence)
            putExtra("timestamp", System.currentTimeMillis())
        }

        // Send to running service
        TripDetectionService.getInstance()?.onActivityDetected(
            mostProbableActivity.type,
            mostProbableActivity.confidence
        )
    }

    private fun getActivityName(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            else -> "UNKNOWN ($type)"
        }
    }
}
