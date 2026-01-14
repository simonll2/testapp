package com.greenmobilitypass.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a locally detected journey.
 * Journeys are stored locally until validated by the user and sent to the backend.
 */
@Entity(tableName = "local_journeys")
data class LocalJourney(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Departure and arrival timestamps (epoch milliseconds)
    val timeDeparture: Long,
    val timeArrival: Long,

    // Calculated duration in minutes
    val durationMinutes: Int,

    // Estimated distance in kilometers
    val distanceKm: Double,

    // Detected transport type: "marche", "velo", "voiture", "transport_commun"
    val detectedTransportType: String,

    // Average confidence of detection (0-100)
    val confidenceAvg: Int,

    // Place names (generic for POC)
    val placeDeparture: String = "Auto-detected",
    val placeArrival: String = "Unknown",

    // Status: "PENDING" or "SENT"
    val status: String = JourneyStatus.PENDING,

    // Timestamps for record tracking
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Constants for journey status
 */
object JourneyStatus {
    const val PENDING = "PENDING"
    const val SENT = "SENT"
}
