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

        // Mode normal: tolérer jusqu'à 2 minutes de STILL (pauses courtes)
        // Avec intervalle de 30s, cela correspond à 4 événements STILL
        private const val STILL_PAUSE_TOLERANCE = 4  // ~2 min de pause tolérée
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

    // Compteur de pauses tolérées pendant un trajet
    private var pauseStillCount = 0

    /**
     * Buffer des événements récents pour calculer le vrai timestamp de début de trajet.
     * On garde les N derniers événements de mouvement consécutifs.
     * Taille max = MIN_TRIP_START_EVENTS pour ne garder que ce qui est pertinent.
     */
    private val recentMovingEvents = mutableListOf<ActivityEvent>()

    /**
     * Timestamp du premier événement STILL lors de la fin de trajet.
     * Utilisé pour backdater correctement la fin de trajet.
     */
    private var firstStillTimestamp: Long = 0

    // Listener for trip detection events
    var onTripDetected: ((DetectedTrip) -> Unit)? = null

    /**
     * Process a new activity event and potentially transition states.
     * @param event The activity event to process
     * @param debugMode Whether debug mode is enabled (from service)
     */
    fun processActivity(event: ActivityEvent, debugMode: Boolean = false) {
        // Log clairement le mode actuel pour faciliter le diagnostic
        val modeLabel = if (debugMode) "[DEBUG MODE]" else "[NORMAL MODE]"
        Log.d(TAG, "$modeLabel Processing: ${event.activityType}, confidence: ${event.confidence}, state: $currentState")

        // ═══════════════════════════════════════════════════════════════════════════
        // 🔧 DEBUG MODE TERRAIN - POC UNIQUEMENT
        // ═══════════════════════════════════════════════════════════════════════════
        // Ce mode est destiné aux tests terrain pendant le développement du POC.
        // Il NE DOIT PAS être utilisé en "production" car il bypass toute la logique
        // de détection normale.
        //
        // Comportement DEBUG:
        // - Bypass TOTAL de la state machine (pas de compteurs, pas de seuils)
        // - Ignore le seuil de confidence (MIN_CONFIDENCE)
        // - Ignore la durée minimale de trajet
        // - Ignore le nombre d'événements consécutifs requis
        // - Crée IMMÉDIATEMENT un trajet factice sur tout mouvement détecté
        // - Trajet factice: 2 minutes, 0.15 km (valeurs réalistes pour tests indoor)
        //
        // ⚠️ GARDE-FOU: Ce bloc ne s'exécute QUE si debugMode == true ET mouvement détecté
        // ═══════════════════════════════════════════════════════════════════════════
        if (debugMode && isMovingActivity(event)) {
            Log.d(TAG, "╔═══════════════════════════════════════════════════════════╗")
            Log.d(TAG, "║  🔧 DEBUG MODE TERRAIN - BYPASS STATE MACHINE ACTIF      ║")
            Log.d(TAG, "╠═══════════════════════════════════════════════════════════╣")
            Log.d(TAG, "║  Activity détectée: ${event.activityType.name.padEnd(40)}║")
            Log.d(TAG, "║  Confidence: ${event.confidence} (IGNORÉ en mode debug)".padEnd(60) + "║")
            Log.d(TAG, "║  Action: Création IMMÉDIATE d'un trajet factice          ║")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════╝")

            val now = System.currentTimeMillis()
            // Durée fixe de 2 minutes (trajet factice réaliste pour tests)
            val durationMinutes = 2
            val durationMs = durationMinutes * 60 * 1000L
            // Distance faible fixe (indoor-ish, réaliste pour tests en intérieur)
            val distanceKm = 0.15

            val detectedTrip = DetectedTrip(
                timeDeparture = now - durationMs,
                timeArrival = now,
                durationMinutes = durationMinutes,
                distanceKm = String.format("%.2f", distanceKm).toDouble(),
                transportType = event.activityType.toTransportType(),
                confidenceAvg = event.confidence
            )

            Log.d(TAG, "🔧 DEBUG: Trajet factice créé -> ${detectedTrip.transportType}, ${detectedTrip.durationMinutes}min, ${detectedTrip.distanceKm}km")

            onTripDetected?.invoke(detectedTrip)
            resetState()
            currentState = TripState.IDLE

            Log.d(TAG, "🔧 DEBUG: State machine reset -> IDLE (prêt pour prochain test)")
            return
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // MODE NORMAL - Logique de détection standard (ci-dessous)
        // ═══════════════════════════════════════════════════════════════════════════
        if (debugMode) {
            // Debug mode actif mais événement STILL → on log quand même pour le diagnostic
            Log.d(TAG, "🔧 DEBUG: Événement STILL ignoré (pas de création de trajet sur immobilité)")
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

            // Stocker l'événement avec son timestamp réel pour backdating précis
            recentMovingEvents.add(event)
            // Garder uniquement les événements nécessaires
            if (recentMovingEvents.size > MIN_TRIP_START_EVENTS) {
                recentMovingEvents.removeAt(0)
            }

            Log.d(TAG, "Moving event detected, consecutive: $consecutiveMovingEvents, buffered: ${recentMovingEvents.size}")

            if (consecutiveMovingEvents >= MIN_TRIP_START_EVENTS) {
                // Start a new trip
                startTrip(event)
            }
        } else {
            // Reset counter and buffer if not a consistent moving activity
            consecutiveMovingEvents = 0
            recentMovingEvents.clear()
        }
    }

    private fun handleInTripState(event: ActivityEvent) {
        // Add event to trip activities for tracking
        if (isMovingActivity(event)) {
            tripActivities.add(event)
            consecutiveStillEvents = 0
            pauseStillCount = 0  // Reset pause counter on movement
            firstStillTimestamp = 0  // Reset first STILL timestamp on movement
        }

        // Check for trip end condition
        if (event.activityType == DetectedActivityType.STILL && event.confidence >= MIN_CONFIDENCE) {
            consecutiveStillEvents++
            pauseStillCount++

            // Mémoriser le timestamp du premier STILL pour backdating précis
            if (firstStillTimestamp == 0L) {
                firstStillTimestamp = event.timestamp
                Log.d(TAG, "First STILL event recorded at timestamp: $firstStillTimestamp")
            }

            Log.d(TAG, "STILL event detected, consecutive: $consecutiveStillEvents, pauseCount: $pauseStillCount")

            // Tolérer les pauses courtes (feux rouges, arrêts de bus, etc.)
            if (pauseStillCount <= STILL_PAUSE_TOLERANCE) {
                Log.d(TAG, "Pause tolérée ($pauseStillCount/$STILL_PAUSE_TOLERANCE), trip continues")
                return  // Ne pas terminer le trajet
            }

            if (consecutiveStillEvents >= MIN_TRIP_END_EVENTS) {
                // End the trip only after extended STILL period
                Log.d(TAG, "Extended STILL period detected, ending trip")
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

        // Utiliser le timestamp RÉEL du premier événement de mouvement (plus précis que 30s × N)
        // Le buffer recentMovingEvents contient les événements avec leurs vrais timestamps
        tripStartTime = if (recentMovingEvents.isNotEmpty()) {
            val realTimestamp = recentMovingEvents.first().timestamp
            Log.d(TAG, "Trip start backdated to REAL timestamp: $realTimestamp")
            realTimestamp
        } else {
            // Fallback (ne devrait pas arriver)
            Log.w(TAG, "No buffered events, using current time as trip start")
            event.timestamp
        }

        tripActivities.clear()
        // Ajouter tous les événements bufferisés au trajet
        tripActivities.addAll(recentMovingEvents)
        recentMovingEvents.clear()

        consecutiveStillEvents = 0
        consecutiveMovingEvents = 0
        firstStillTimestamp = 0
    }

    private fun endTrip(event: ActivityEvent) {
        Log.d(TAG, "Ending trip")
        currentState = TripState.ENDED

        // Utiliser le timestamp RÉEL du premier STILL (plus précis que 30s × N)
        tripEndTime = if (firstStillTimestamp > 0) {
            Log.d(TAG, "Trip end backdated to REAL first STILL timestamp: $firstStillTimestamp")
            firstStillTimestamp
        } else {
            // Fallback (ne devrait pas arriver)
            Log.w(TAG, "No first STILL timestamp, using current time as trip end")
            event.timestamp
        }
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
        recentMovingEvents.clear()
        consecutiveStillEvents = 0
        consecutiveMovingEvents = 0
        pauseStillCount = 0
        tripStartTime = 0
        tripEndTime = 0
        firstStillTimestamp = 0
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
