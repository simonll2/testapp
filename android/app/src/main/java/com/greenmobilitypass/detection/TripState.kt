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
            WALKING, RUNNING -> "marche"
            ON_BICYCLE -> "velo"
            IN_VEHICLE -> "voiture"
            STILL, UNKNOWN -> "marche" // Default fallback
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
         * Convert from Google's DetectedActivity type constant.
         *
         * Mapping des constantes Google Activity Recognition :
         * - 0 = IN_VEHICLE (véhicule motorisé)
         * - 1 = ON_BICYCLE (vélo)
         * - 2 = ON_FOOT (déplacement à pied, catégorie générique)
         * - 3 = STILL (immobile)
         * - 7 = WALKING (marche, sous-type de ON_FOOT)
         * - 8 = RUNNING (course, sous-type de ON_FOOT)
         *
         * Note: ON_FOOT (2) est renvoyé par Google quand l'utilisateur se déplace
         * à pied mais le système n'est pas sûr s'il marche ou court.
         * C'est une activité de MOUVEMENT, pas d'immobilité.
         */
        fun fromGoogleActivityType(type: Int): DetectedActivityType {
            return when (type) {
                0 -> IN_VEHICLE      // DetectedActivity.IN_VEHICLE
                1 -> ON_BICYCLE      // DetectedActivity.ON_BICYCLE
                2 -> WALKING         // DetectedActivity.ON_FOOT → traité comme marche (c'est un déplacement!)
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
