package com.greenmobilitypass.detection

import android.util.Log

/**
 * State machine for detecting trips based on Activity Recognition events.
 *
 * Rules:
 * - Start trip: activity != STILL, confidence >= 60, duration >= 2 minutes
 * - End trip: STILL, confidence >= 60, duration >= 3 minutes
 * - Dominant mode = most frequent activity during trip
 * - Distance = duration_hours * estimated_speed
 */
class TripStateMachine {

    companion object {
        private const val TAG = "TripStateMachine"
        private const val MIN_CONFIDENCE = 60
        private const val MIN_TRIP_START_EVENTS = 4  // ~2 min with 30s intervals
        private const val MIN_TRIP_END_EVENTS = 6    // ~3 min with 30s intervals
    }

    private var currentState: TripState = TripState.IDLE
    private var tripStartTime: Long = 0
    private var tripEndTime: Long = 0

    // Track activities during current trip
    private val tripActivities = mutableListOf<ActivityEvent>()

    // Track consecutive STILL events for trip end detection
    private var consecutiveStillEvents = 0

    // Track consecutive moving events for trip start detection
    private var consecutiveMovingEvents = 0

    // Listener for trip detection events
    var onTripDetected: ((DetectedTrip) -> Unit)? = null

    /**
     * Process a new activity event and potentially transition states.
     * @param event The activity event to process
     * @param debugMode Whether debug mode is enabled (from service)
     */
    fun processActivity(event: ActivityEvent, debugMode: Boolean = false) {
        Log.d(TAG, "Processing activity: ${event.activityType}, confidence: ${event.confidence}, state: $currentState, debugMode: $debugMode")

        /**
         * DEBUG MODE:
         * - Does NOT mock Activity Recognition API
         * - Requires at least one real activity event
         * - Bypasses trip duration and end conditions
         * - Creates a trip immediately on first movement
         */
        if (debugMode && isMovingActivity(event)) {
            Log.d(TAG, "DEBUG MODE ACTIVE - forcing trip for ${event.activityType} with confidence ${event.confidence}")

            val now = System.currentTimeMillis()
            val twoMinutesMs = 2 * 60 * 1000L
            val durationMinutes = 2
            val distanceKm = 0.15 // small indoor-ish distance

            val detectedTrip = DetectedTrip(
                timeDeparture = now - twoMinutesMs,
                timeArrival = now,
                durationMinutes = durationMinutes,
                distanceKm = String.format("%.2f", distanceKm).toDouble(),
                transportType = event.activityType.toTransportType(),
                confidenceAvg = event.confidence
            )

            onTripDetected?.invoke(detectedTrip)
            resetState()
            currentState = TripState.IDLE
            return
        }

        when (currentState) {
            TripState.IDLE -> handleIdleState(event)
            TripState.IN_TRIP -> handleInTripState(event)
            TripState.ENDED -> handleEndedState(event)
        }
    }

    private fun handleIdleState(event: ActivityEvent) {
        // Check if this is a moving activity with sufficient confidence
        if (isMovingActivity(event) && event.confidence >= MIN_CONFIDENCE) {
            consecutiveMovingEvents++
            Log.d(TAG, "Moving event detected, consecutive: $consecutiveMovingEvents")

            if (consecutiveMovingEvents >= MIN_TRIP_START_EVENTS) {
                // Start a new trip
                startTrip(event)
            }
        } else {
            // Reset counter if not a consistent moving activity
            consecutiveMovingEvents = 0
        }
    }

    private fun handleInTripState(event: ActivityEvent) {
        // Add event to trip activities for tracking
        if (isMovingActivity(event)) {
            tripActivities.add(event)
            consecutiveStillEvents = 0
        }

        // Check for trip end condition
        if (event.activityType == DetectedActivityType.STILL && event.confidence >= MIN_CONFIDENCE) {
            consecutiveStillEvents++
            Log.d(TAG, "STILL event detected, consecutive: $consecutiveStillEvents")

            if (consecutiveStillEvents >= MIN_TRIP_END_EVENTS) {
                // End the trip
                endTrip(event)
            }
        }
    }

    private fun handleEndedState(event: ActivityEvent) {
        // Trip has ended, finalize and save
        finalizeTrip()

        // Reset to IDLE and check if new trip is starting
        currentState = TripState.IDLE
        if (isMovingActivity(event) && event.confidence >= MIN_CONFIDENCE) {
            consecutiveMovingEvents = 1
        }
    }

    private fun startTrip(event: ActivityEvent) {
        Log.d(TAG, "Starting new trip")
        currentState = TripState.IN_TRIP
        tripStartTime = System.currentTimeMillis() - (MIN_TRIP_START_EVENTS * 30000L) // Backdate to first moving event
        tripActivities.clear()
        tripActivities.add(event)
        consecutiveStillEvents = 0
        consecutiveMovingEvents = 0
    }

    private fun endTrip(event: ActivityEvent) {
        Log.d(TAG, "Ending trip")
        currentState = TripState.ENDED
        tripEndTime = System.currentTimeMillis() - (MIN_TRIP_END_EVENTS * 30000L) // Backdate to first STILL event
    }

    private fun finalizeTrip() {
        if (tripActivities.isEmpty()) {
            Log.w(TAG, "No activities recorded for trip, skipping")
            resetState()
            return
        }

        // Calculate trip details
        val durationMs = tripEndTime - tripStartTime
        val durationMinutes = (durationMs / 60000).toInt()

        if (durationMinutes < 2) {
            Log.w(TAG, "Trip too short ($durationMinutes min), skipping")
            resetState()
            return
        }

        // Find dominant activity type
        val dominantActivity = findDominantActivity()

        // Calculate average confidence
        val avgConfidence = tripActivities.map { it.confidence }.average().toInt()

        // Calculate estimated distance
        val durationHours = durationMinutes / 60.0
        val distanceKm = durationHours * dominantActivity.getEstimatedSpeedKmh()

        val detectedTrip = DetectedTrip(
            timeDeparture = tripStartTime,
            timeArrival = tripEndTime,
            durationMinutes = durationMinutes,
            distanceKm = String.format("%.2f", distanceKm).toDouble(),
            transportType = dominantActivity.toTransportType(),
            confidenceAvg = avgConfidence
        )

        Log.d(TAG, "Trip finalized: $detectedTrip")
        onTripDetected?.invoke(detectedTrip)

        resetState()
    }

    private fun findDominantActivity(): DetectedActivityType {
        if (tripActivities.isEmpty()) {
            return DetectedActivityType.WALKING
        }

        // Count occurrences of each activity type (excluding STILL and UNKNOWN)
        val activityCounts = tripActivities
            .filter { it.activityType != DetectedActivityType.STILL && it.activityType != DetectedActivityType.UNKNOWN }
            .groupingBy { it.activityType }
            .eachCount()

        return activityCounts.maxByOrNull { it.value }?.key ?: DetectedActivityType.WALKING
    }

    private fun isMovingActivity(event: ActivityEvent): Boolean {
        return event.activityType != DetectedActivityType.STILL &&
               event.activityType != DetectedActivityType.UNKNOWN
    }

    private fun resetState() {
        tripActivities.clear()
        consecutiveStillEvents = 0
        consecutiveMovingEvents = 0
        tripStartTime = 0
        tripEndTime = 0
    }

    /**
     * Get current state for debugging
     */
    fun getCurrentState(): TripState = currentState

    /**
     * Check if currently tracking a trip
     */
    fun isTrackingTrip(): Boolean = currentState == TripState.IN_TRIP

    /**
     * Force reset the state machine (e.g., when service is stopped)
     */
    fun reset() {
        currentState = TripState.IDLE
        resetState()
    }
}
