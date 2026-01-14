package com.greenmobilitypass.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for LocalJourney entity.
 * Provides methods for CRUD operations on local journeys.
 */
@Dao
interface LocalJourneyDao {

    /**
     * Get all pending journeys (not yet sent to backend)
     */
    @Query("SELECT * FROM local_journeys WHERE status = :status ORDER BY timeDeparture DESC")
    suspend fun getJourneysByStatus(status: String = JourneyStatus.PENDING): List<LocalJourney>

    /**
     * Get pending journeys as Flow for reactive updates
     */
    @Query("SELECT * FROM local_journeys WHERE status = 'PENDING' ORDER BY timeDeparture DESC")
    fun getPendingJourneysFlow(): Flow<List<LocalJourney>>

    /**
     * Get a specific journey by ID
     */
    @Query("SELECT * FROM local_journeys WHERE id = :id")
    suspend fun getJourney(id: Long): LocalJourney?

    /**
     * Insert a new journey
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: LocalJourney): Long

    /**
     * Update an existing journey
     */
    @Update
    suspend fun updateJourney(journey: LocalJourney)

    /**
     * Delete a journey by ID
     */
    @Query("DELETE FROM local_journeys WHERE id = :id")
    suspend fun deleteJourney(id: Long)

    /**
     * Mark a journey as sent to backend
     */
    @Query("UPDATE local_journeys SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSent(
        id: Long,
        status: String = JourneyStatus.SENT,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Get count of pending journeys
     */
    @Query("SELECT COUNT(*) FROM local_journeys WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    /**
     * Delete all sent journeys (cleanup)
     */
    @Query("DELETE FROM local_journeys WHERE status = 'SENT'")
    suspend fun deleteSentJourneys()
}
