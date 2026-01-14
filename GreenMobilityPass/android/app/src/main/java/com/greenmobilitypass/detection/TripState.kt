package com.greenmobilitypass.detection

/**
 * States for the trip detection state machine.
 */
enum class TripState {
    IDLE,       // No trip in progress, waiting for activity
    IN_TRIP,    // Trip in progress
    ENDED       // Trip ended, ready to save
}

/**
 * Detected activity types from Activity Recognition API.
 * Maps Google's DetectedActivity types to our transport types.
 */
enum class DetectedActivityType {
    WALKING,
    RUNNING,
    ON_BICYCLE,
    IN_VEHICLE,
    STILL,
    UNKNOWN;

    /**
     * Convert to backend transport type string
     */
    fun toTransportType(): String {
        return when (this) {
            WALKING, RUNNING -> "apied"
            ON_BICYCLE -> "velo"
            IN_VEHICLE -> "voiture"
            STILL, UNKNOWN -> "apied" // Default fallback
        }
    }

    /**
     * Get estimated speed in km/h for distance calculation
     */
    fun getEstimatedSpeedKmh(): Double {
        return when (this) {
            WALKING -> 5.0
            RUNNING -> 10.0
            ON_BICYCLE -> 15.0
            IN_VEHICLE -> 40.0
            STILL, UNKNOWN -> 0.0
        }
    }

    companion object {
        /**
         * Convert from Google's DetectedActivity type constant
         */
        fun fromGoogleActivityType(type: Int): DetectedActivityType {
            return when (type) {
                0 -> IN_VEHICLE      // DetectedActivity.IN_VEHICLE
                1 -> ON_BICYCLE      // DetectedActivity.ON_BICYCLE
                2 -> STILL           // DetectedActivity.ON_FOOT - treat as walking
                3 -> STILL           // DetectedActivity.STILL
                7 -> WALKING         // DetectedActivity.WALKING
                8 -> RUNNING         // DetectedActivity.RUNNING
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Data class representing a detected activity event
 */
data class ActivityEvent(
    val activityType: DetectedActivityType,
    val confidence: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data class representing a detected trip ready to be saved
 */
data class DetectedTrip(
    val timeDeparture: Long,
    val timeArrival: Long,
    val durationMinutes: Int,
    val distanceKm: Double,
    val transportType: String,
    val confidenceAvg: Int
)
