package com.greenmobilitypass.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * BroadcastReceiver for Activity Recognition updates.
 * Receives activity detection results from Google Play Services and
 * forwards them to the TripDetectionService.
 *
 * Inclut un mécanisme de bufferisation pour ne pas perdre le premier événement
 * si le service n'est pas encore prêt.
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityRecognition"
        const val ACTION_ACTIVITY_UPDATE = "com.greenmobilitypass.ACTION_ACTIVITY_UPDATE"

        // Buffer pour événement en attente si service pas prêt
        private var bufferedActivityType: Int? = null
        private var bufferedConfidence: Int? = null
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 500L
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
        val activityType = mostProbableActivity.type
        val confidence = mostProbableActivity.confidence

        Log.d(TAG, "Activity detected: ${getActivityName(activityType)}, confidence: $confidence")

        // Ensure service is running before forwarding event
        val service = TripDetectionService.getInstance()
        if (service == null) {
            Log.w(TAG, "Service not running, buffering event and starting service")

            // Bufferiser l'événement
            bufferedActivityType = activityType
            bufferedConfidence = confidence

            val serviceIntent = Intent(context, TripDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Lancer un thread pour rejouer l'événement une fois le service prêt
            Thread {
                replayBufferedEvent()
            }.start()

            return
        }

        // Send to running service
        service.onActivityDetected(activityType, confidence)
    }

    /**
     * Rejoue l'événement bufferisé une fois le service prêt.
     * Mécanisme simple avec retry pour POC.
     */
    private fun replayBufferedEvent() {
        val type = bufferedActivityType ?: return
        val conf = bufferedConfidence ?: return

        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            Thread.sleep(RETRY_DELAY_MS * attempt)

            val service = TripDetectionService.getInstance()
            if (service != null) {
                Log.d(TAG, "Service ready, replaying buffered event: ${getActivityName(type)} confidence=$conf")
                service.onActivityDetected(type, conf)

                // Clear buffer
                bufferedActivityType = null
                bufferedConfidence = null
                return
            }

            Log.d(TAG, "Service not ready yet, attempt $attempt/$MAX_RETRY_ATTEMPTS")
        }

        Log.w(TAG, "Could not replay buffered event after $MAX_RETRY_ATTEMPTS attempts")
        // Clear buffer anyway
        bufferedActivityType = null
        bufferedConfidence = null
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
