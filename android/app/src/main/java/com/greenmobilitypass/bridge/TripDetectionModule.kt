package com.greenmobilitypass.bridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.greenmobilitypass.database.AppDatabase
import com.greenmobilitypass.database.JourneyStatus
import com.greenmobilitypass.database.LocalJourney
import com.greenmobilitypass.detection.BootReceiver
import com.greenmobilitypass.detection.TripDetectionService
import kotlinx.coroutines.*

/**
 * React Native Native Module for trip detection.
 * Fix: Android 14+ location FGS requirements (FGS type "location" requires COARSE or FINE runtime permission),
 * while keeping the original JS-exposed API intact.
 */
class TripDetectionModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "TripDetectionModule"
        private const val MODULE_NAME = "TripDetectionModule"
        private const val PERMISSION_REQUEST_CODE = 1001

        // Event names for React Native
        const val EVENT_TRIP_DETECTED = "onTripDetected"
        const val EVENT_DETECTION_STATE_CHANGED = "onDetectionStateChanged"
        
        // Debug/demo mode flag (POC) - can be toggled from React Native
        @JvmField
        var debugMode: Boolean = false
    }

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(reactApplicationContext)
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun getName(): String = MODULE_NAME

    override fun getConstants(): MutableMap<String, Any> {
        return hashMapOf(
            "EVENT_TRIP_DETECTED" to EVENT_TRIP_DETECTED,
            "EVENT_DETECTION_STATE_CHANGED" to EVENT_DETECTION_STATE_CHANGED
        )
    }

    /**
     * Start trip detection service
     */
    @ReactMethod
    fun startDetection(promise: Promise) {
        try {
            val context = reactApplicationContext

            // Check permissions first (CRITICAL for Android 14+ FGS type location)
            if (!hasRequiredPermissions()) {
                Log.w(TAG, "Required permissions not granted, cannot start detection")
                promise.reject("PERMISSION_DENIED", "Required permissions not granted")
                return
            }

            val serviceIntent = Intent(context, TripDetectionService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Save preference for boot restart
            BootReceiver.setDetectionEnabled(context, true)

            // Set up trip detection listener
            setupTripListener()

            Log.d(TAG, "Detection service started")
            promise.resolve(true)

            sendEvent(
                EVENT_DETECTION_STATE_CHANGED,
                Arguments.createMap().apply { putBoolean("isRunning", true) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start detection", e)
            promise.reject("START_FAILED", e.message)
        }
    }

    /**
     * Stop trip detection service
     */
    @ReactMethod
    fun stopDetection(promise: Promise) {
        try {
            val context = reactApplicationContext
            val serviceIntent = Intent(context, TripDetectionService::class.java)
            context.stopService(serviceIntent)

            // Save preference
            BootReceiver.setDetectionEnabled(context, false)

            Log.d(TAG, "Detection service stopped")
            promise.resolve(true)

            sendEvent(
                EVENT_DETECTION_STATE_CHANGED,
                Arguments.createMap().apply { putBoolean("isRunning", false) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop detection", e)
            promise.reject("STOP_FAILED", e.message)
        }
    }

    /**
     * Check if detection service is running
     */
    @ReactMethod
    fun isDetectionRunning(promise: Promise) {
        promise.resolve(TripDetectionService.isRunning())
    }

    /**
     * Get all pending journeys
     */
    @ReactMethod
    fun getPendingJourneys(promise: Promise) {
        scope.launch {
            try {
                val journeys = withContext(Dispatchers.IO) {
                    database.localJourneyDao().getJourneysByStatus(JourneyStatus.PENDING)
                }

                val result = Arguments.createArray()
                journeys.forEach { journey ->
                    result.pushMap(journeyToMap(journey))
                }

                promise.resolve(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get pending journeys", e)
                promise.reject("GET_FAILED", e.message)
            }
        }
    }

    /**
     * Get a specific journey by ID
     */
    @ReactMethod
    fun getJourney(id: Double, promise: Promise) {
        scope.launch {
            try {
                val journey = withContext(Dispatchers.IO) {
                    database.localJourneyDao().getJourney(id.toLong())
                }

                if (journey != null) {
                    promise.resolve(journeyToMap(journey))
                } else {
                    promise.reject("NOT_FOUND", "Journey not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get journey", e)
                promise.reject("GET_FAILED", e.message)
            }
        }
    }

    /**
     * Update a local journey
     */
    @ReactMethod
    fun updateLocalJourney(id: Double, updates: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val journey = database.localJourneyDao().getJourney(id.toLong())
                    if (journey != null) {
                        val updatedJourney = journey.copy(
                            detectedTransportType =
                                if (updates.hasKey("transportType"))
                                    updates.getString("transportType") ?: journey.detectedTransportType
                                else journey.detectedTransportType,
                            distanceKm =
                                if (updates.hasKey("distanceKm")) updates.getDouble("distanceKm")
                                else journey.distanceKm,
                            placeDeparture =
                                if (updates.hasKey("placeDeparture"))
                                    updates.getString("placeDeparture") ?: journey.placeDeparture
                                else journey.placeDeparture,
                            placeArrival =
                                if (updates.hasKey("placeArrival"))
                                    updates.getString("placeArrival") ?: journey.placeArrival
                                else journey.placeArrival,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.localJourneyDao().updateJourney(updatedJourney)
                    } else {
                        throw Exception("Journey not found")
                    }
                }
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update journey", e)
                promise.reject("UPDATE_FAILED", e.message)
            }
        }
    }

    /**
     * Delete a local journey
     */
    @ReactMethod
    fun deleteLocalJourney(id: Double, promise: Promise) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.localJourneyDao().deleteJourney(id.toLong())
                }
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete journey", e)
                promise.reject("DELETE_FAILED", e.message)
            }
        }
    }

    /**
     * Mark a journey as sent to backend
     */
    @ReactMethod
    fun markJourneySent(id: Double, promise: Promise) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.localJourneyDao().markSent(id.toLong())
                }
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark journey as sent", e)
                promise.reject("MARK_FAILED", e.message)
            }
        }
    }

    /**
     * Get count of pending journeys
     */
    @ReactMethod
    fun getPendingCount(promise: Promise) {
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    database.localJourneyDao().getPendingCount()
                }
                promise.resolve(count)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get pending count", e)
                promise.reject("COUNT_FAILED", e.message)
            }
        }
    }

    /**
     * Check if required permissions are granted
     */
    @ReactMethod
    fun checkPermissions(promise: Promise) {
        val result = Arguments.createMap().apply {
            putBoolean("activityRecognition", hasActivityRecognitionPermission())
            putBoolean("location", hasAnyLocationPermission())
            putBoolean("notifications", hasNotificationPermission())
            putBoolean("allGranted", hasRequiredPermissions())
        }
        promise.resolve(result)
    }

    /**
     * Request required permissions
     */
    @ReactMethod
    fun requestPermissions(promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No activity available")
            return
        }

        val permissions = mutableListOf<String>()

        if (!hasActivityRecognitionPermission()) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Android Studio suggested declaring both; runtime we request both to be explicit.
        if (!hasCoarseLocationPermission()) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!hasFineLocationPermission()) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isEmpty()) {
            promise.resolve(true)
            return
        }

        ActivityCompat.requestPermissions(
            activity,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )

        // POC choice: JS should call checkPermissions() again (or try startDetection()) after user action
        promise.resolve(false)
    }

    private fun hasRequiredPermissions(): Boolean {
        // For FGS type location on targetSdk 34+, you must hold FOREGROUND_SERVICE_LOCATION (manifest)
        // AND at runtime have at least COARSE or FINE location granted.
        return hasActivityRecognitionPermission() &&
                hasAnyLocationPermission() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission())
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            reactApplicationContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            reactApplicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            reactApplicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAnyLocationPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                reactApplicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun setupTripListener() {
        TripDetectionService.getInstance()?.onTripDetectedListener = { journey ->
            sendEvent(EVENT_TRIP_DETECTED, journeyToMap(journey))
        }
    }

    /**
     * Enable or disable debug/demo mode from React Native.
     * When enabled, the native state machine may force small trips for debugging.
     */
    @ReactMethod
    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        Log.d(TAG, "Debug mode set: $enabled")
        // Notify JS that detection state did not change, but useful for UI
        sendEvent(
            EVENT_DETECTION_STATE_CHANGED,
            Arguments.createMap().apply { putBoolean("debugMode", enabled) }
        )
    }

    /**
     * Create a fake LocalJourney and insert into Room, then notify JS.
     * Used to test the full flow (Room -> UI -> backend) from the mobile app UI.
     */
    @ReactMethod
    fun simulateTrip() {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val tenMinutesMs = 10 * 60 * 1000L
                val localJourney = LocalJourney(
                    timeDeparture = now - tenMinutesMs,
                    timeArrival = now,
                    durationMinutes = 10,
                    distanceKm = 0.8,
                    detectedTransportType = "marche",
                    confidenceAvg = 80,
                    placeDeparture = "DEBUG: Simulated",
                    placeArrival = "DEBUG: Simulated"
                )

                val id = withContext(Dispatchers.IO) {
                    database.localJourneyDao().insertJourney(localJourney)
                }

                val saved = localJourney.copy(id = id)
                Log.d(TAG, "Simulated journey inserted with id=$id")

                // Emit event to React Native so UI can react as if a trip was detected
                sendEvent(EVENT_TRIP_DETECTED, journeyToMap(saved))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to simulate trip", e)
            }
        }
    }

    private fun journeyToMap(journey: LocalJourney): WritableMap {
        return Arguments.createMap().apply {
            putDouble("id", journey.id.toDouble())
            putDouble("timeDeparture", journey.timeDeparture.toDouble())
            putDouble("timeArrival", journey.timeArrival.toDouble())
            putInt("durationMinutes", journey.durationMinutes)
            putDouble("distanceKm", journey.distanceKm)
            putString("detectedTransportType", journey.detectedTransportType)
            putInt("confidenceAvg", journey.confidenceAvg)
            putString("placeDeparture", journey.placeDeparture)
            putString("placeArrival", journey.placeArrival)
            putString("status", journey.status)
            putDouble("createdAt", journey.createdAt.toDouble())
            putDouble("updatedAt", journey.updatedAt.toDouble())
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, params)
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for RN event emitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for RN event emitter
    }

    override fun invalidate() {
        super.invalidate()
        scope.cancel()
    }
}
