package com.greenmobilitypass.detection

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.DetectedActivity
import com.greenmobilitypass.MainActivity
import com.greenmobilitypass.R
import com.greenmobilitypass.database.AppDatabase
import com.greenmobilitypass.database.LocalJourney
import kotlinx.coroutines.launch

/**
 * Foreground Service for detecting trips in the background.
 * Uses Activity Recognition API to detect user movement and creates
 * local journey records when trips are detected.
 */
class TripDetectionService : LifecycleService() {

    companion object {
        private const val TAG = "TripDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "trip_detection_channel"
        private const val ACTIVITY_DETECTION_INTERVAL_MS = 30000L // 30 seconds
        private const val PREFS_NAME = "trip_detection_prefs"
        private const val KEY_DEBUG_MODE = "debug_mode"

        @Volatile
        private var instance: TripDetectionService? = null

        fun getInstance(): TripDetectionService? = instance

        fun isRunning(): Boolean = instance != null
    }

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var pendingIntent: PendingIntent
    private lateinit var database: AppDatabase
    private val stateMachine = TripStateMachine()

    // Debug mode flag - moved from TripDetectionModule to service for reliability
    var debugMode: Boolean = false

    // Listener for React Native events
    var onTripDetectedListener: ((LocalJourney) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "Service onCreate")

        // Restore debug mode from SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        debugMode = prefs.getBoolean(KEY_DEBUG_MODE, false)
        Log.d(TAG, "Debug mode restored: $debugMode")

        database = AppDatabase.getInstance(applicationContext)
        activityRecognitionClient = ActivityRecognition.getClient(this)

        // Setup state machine callback
        stateMachine.onTripDetected = { trip ->
            saveTrip(trip)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Log.d(TAG, "Service onStartCommand")

        // Start as foreground service
        startForegroundService()

        // Register for activity updates
        registerActivityRecognition()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        // Unregister activity recognition
        unregisterActivityRecognition()

        // Reset state machine
        stateMachine.reset()

        instance = null
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startForegroundService() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors your trips for green mobility tracking"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Green Mobility Pass")
            .setContentText("Détection de trajets active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun registerActivityRecognition() {
        Log.d(TAG, "Registering Activity Recognition with ${ACTIVITY_DETECTION_INTERVAL_MS}ms interval")

        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        activityRecognitionClient
            .requestActivityUpdates(ACTIVITY_DETECTION_INTERVAL_MS, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Activity Recognition registered successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register activity recognition", e)
            }
    }

    private fun unregisterActivityRecognition() {
        if (::pendingIntent.isInitialized) {
            activityRecognitionClient
                .removeActivityUpdates(pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Activity recognition unregistered")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to unregister activity recognition", e)
                }
        }
    }

    /**
     * Called by ActivityRecognitionReceiver when activity is detected
     */
    fun onActivityDetected(activityType: Int, confidence: Int) {
        Log.d(TAG, "onActivityDetected: type=$activityType confidence=$confidence debugMode=$debugMode")

        val detectedType = DetectedActivityType.fromGoogleActivityType(activityType)
        val event = ActivityEvent(
            activityType = detectedType,
            confidence = confidence
        )

        stateMachine.processActivity(event, debugMode)
    }

    private fun saveTrip(trip: DetectedTrip) {
        lifecycleScope.launch {
            try {
                val localJourney = LocalJourney(
                    timeDeparture = trip.timeDeparture,
                    timeArrival = trip.timeArrival,
                    durationMinutes = trip.durationMinutes,
                    distanceKm = trip.distanceKm,
                    detectedTransportType = trip.transportType,
                    confidenceAvg = trip.confidenceAvg,
                    placeDeparture = "Auto-detected",
                    placeArrival = "Unknown"
                )

                // Save to database first - this ensures persistence even if listener is null
                val id = database.localJourneyDao().insertJourney(localJourney)
                Log.d(TAG, "Trip saved with ID: $id")

                // Notify listener (React Native) if attached
                // Note: Listener may be null if service was restarted by system
                // UI will still show trips via getPendingJourneys() call on screen focus
                val savedJourney = localJourney.copy(id = id)
                onTripDetectedListener?.invoke(savedJourney)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save trip", e)
            }
        }
    }

    /**
     * Get current detection state for debugging
     */
    fun getCurrentState(): String = stateMachine.getCurrentState().name

    /**
     * Check if currently tracking a trip
     */
    fun isTrackingTrip(): Boolean = stateMachine.isTrackingTrip()

    /**
     * Set debug mode - enables immediate trip detection on any movement
     * Persisted across service restarts
     */
    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        // Persist to SharedPreferences for session persistence
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        Log.d(TAG, "Debug mode set to: $enabled (persisted)")
    }
}
