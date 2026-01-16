package com.greenmobilitypass.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * BroadcastReceiver for Activity Recognition updates.
 * Receives activity detection results from Google Play Services and
 * forwards them to the TripDetectionService.
 *
 * Inclut un mécanisme de bufferisation FIFO pour ne pas perdre les événements
 * si le service n'est pas encore prêt. Contrairement à l'ancien buffer mono-événement,
 * cette implémentation conserve les derniers événements (max 5) et les rejoue dans l'ordre.
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    /**
     * Data class pour stocker un événement bufferisé avec son timestamp.
     */
    private data class BufferedEvent(
        val activityType: Int,
        val confidence: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "ActivityRecognition"
        const val ACTION_ACTIVITY_UPDATE = "com.greenmobilitypass.ACTION_ACTIVITY_UPDATE"

        /**
         * Buffer FIFO thread-safe pour événements en attente si service pas prêt.
         * Taille max: 5 événements (évite la perte de transitions importantes).
         * ConcurrentLinkedQueue garantit la thread-safety sans locks explicites.
         */
        private val eventBuffer = ConcurrentLinkedQueue<BufferedEvent>()
        private const val MAX_BUFFER_SIZE = 5

        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 500L

        // Flag pour éviter les replays concurrents
        @Volatile
        private var replayInProgress = false
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
            Log.w(TAG, "Service not running, buffering event to FIFO queue")

            // Ajouter l'événement au buffer FIFO
            addToBuffer(BufferedEvent(activityType, confidence))

            Log.d(TAG, "Buffer size: ${eventBuffer.size}, starting service...")

            val serviceIntent = Intent(context, TripDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Lancer un thread pour rejouer les événements une fois le service prêt
            // Éviter les replays concurrents
            if (!replayInProgress) {
                Thread {
                    replayBufferedEvents()
                }.start()
            }

            return
        }

        // Send to running service
        service.onActivityDetected(activityType, confidence)
    }

    /**
     * Ajoute un événement au buffer FIFO.
     * Si le buffer est plein, supprime le plus ancien événement (FIFO).
     * Thread-safe grâce à ConcurrentLinkedQueue.
     */
    private fun addToBuffer(event: BufferedEvent) {
        // Si buffer plein, retirer le plus ancien
        while (eventBuffer.size >= MAX_BUFFER_SIZE) {
            val removed = eventBuffer.poll()
            Log.d(TAG, "Buffer full, dropped oldest event: ${getActivityName(removed?.activityType ?: -1)}")
        }
        eventBuffer.offer(event)
    }

    /**
     * Rejoue TOUS les événements bufferisés une fois le service prêt.
     * Les événements sont rejoués dans l'ordre FIFO (du plus ancien au plus récent).
     * Mécanisme simple avec retry pour POC.
     */
    private fun replayBufferedEvents() {
        if (eventBuffer.isEmpty()) {
            return
        }

        replayInProgress = true
        Log.d(TAG, "Starting replay of ${eventBuffer.size} buffered events")

        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            Thread.sleep(RETRY_DELAY_MS * attempt)

            val service = TripDetectionService.getInstance()
            if (service != null) {
                Log.d(TAG, "Service ready, replaying ${eventBuffer.size} buffered events in FIFO order")

                // Rejouer tous les événements dans l'ordre FIFO
                var replayedCount = 0
                while (eventBuffer.isNotEmpty()) {
                    val event = eventBuffer.poll() ?: break
                    Log.d(TAG, "Replaying event ${replayedCount + 1}: ${getActivityName(event.activityType)} confidence=${event.confidence}")
                    service.onActivityDetected(event.activityType, event.confidence)
                    replayedCount++
                }

                Log.d(TAG, "Successfully replayed $replayedCount events")
                replayInProgress = false
                return
            }

            Log.d(TAG, "Service not ready yet, attempt $attempt/$MAX_RETRY_ATTEMPTS")
        }

        Log.w(TAG, "Could not replay buffered events after $MAX_RETRY_ATTEMPTS attempts")
        // Vider le buffer de toute façon pour éviter l'accumulation
        val lostCount = eventBuffer.size
        eventBuffer.clear()
        Log.w(TAG, "Dropped $lostCount events due to service unavailability")
        replayInProgress = false
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
