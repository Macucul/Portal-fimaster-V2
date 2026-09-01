package com.example.data

/**
 * Small integration helper to delegate raw JSON events to EventIngestion.
 *
 * Usage: call this from your Firebase listeners or from any place that receives raw
 * event JSON strings. It centralizes the single-line call and documents the
 * required parameters.
 */
object EventIngestionIntegration {

    /**
     * Process a raw JSON event using the central EventIngestion utility.
     * - eaRobotEventDao: DAO used to persist events into Room
     * - rawJson: the JSON payload received from Firebase/Gateway
     * - firebaseUrl/authKey: optional, forwarded to EventIngestion for ping/status routing
     *
     * Returns true on successful ingestion/upsert, false otherwise.
     */
    suspend fun processAndUpsertEvent(
        eaRobotEventDao: EaRobotEventDao,
        rawJson: String,
        firebaseUrl: String = "",
        authKey: String = ""
    ): Boolean {
        return EventIngestion.ingestRawEvent(eaRobotEventDao, rawJson, firebaseUrl, authKey)
    }
}
