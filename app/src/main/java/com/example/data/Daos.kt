package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileDirect(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)

    @Query("UPDATE user_profile SET mt5AccountId = :accountId WHERE id = 1")
    suspend fun updateMt5AccountId(accountId: String)

    @Query("UPDATE user_profile SET passwordHash = :newPassword WHERE id = 1")
    suspend fun updatePassword(newPassword: String)
}

@Dao
interface RefundRequestDao {
    @Query("SELECT * FROM refund_requests ORDER BY id DESC")
    fun getAllRefundRequests(): Flow<List<RefundRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefundRequest(request: RefundRequest)
}

@Dao
interface EaConfigDao {
    @Query("SELECT * FROM ea_config WHERE mt5AccountId = :accountId LIMIT 1")
    fun getEaConfig(accountId: String): Flow<EaConfigEntity?>

    @Query("SELECT * FROM ea_config WHERE mt5AccountId = :accountId LIMIT 1")
    suspend fun getEaConfigSync(accountId: String): EaConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEaConfig(config: EaConfigEntity)
}

@Dao
interface EaRobotEventDao {
    @Query("SELECT * FROM ea_robot_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<EaRobotEventEntity>>

    @Query("SELECT * FROM ea_robot_events WHERE login = :accountId OR (login == 0 AND id = :accountIdStr) OR (:accountId = 0 AND :accountIdStr = '') ORDER BY timestamp DESC")
    fun getEventsForAccount(accountId: Long, accountIdStr: String): Flow<List<EaRobotEventEntity>>

    @Query("SELECT * FROM ea_robot_events WHERE gid = :gid LIMIT 1")
    suspend fun getEventByGid(gid: String): EaRobotEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEvents(events: List<EaRobotEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEvent(event: EaRobotEventEntity)

    @Query("SELECT MAX(timestamp) FROM ea_robot_events WHERE login = :accountId OR (login == 0 AND id = :accountIdStr) OR (:accountId = 0 AND :accountIdStr = '')")
    suspend fun getMaxTimestamp(accountId: Long, accountIdStr: String): Long?

    @Query("SELECT gid FROM ea_robot_events WHERE login = :accountId OR (login == 0 AND id = :accountIdStr) OR (:accountId = 0 AND :accountIdStr = '') ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastGid(accountId: Long, accountIdStr: String): String?

    @Query("SELECT COUNT(*) FROM ea_robot_events WHERE login = :accountId OR (login == 0 AND id = :accountIdStr) OR (:accountId = 0 AND :accountIdStr = '')")
    suspend fun getEventsCount(accountId: Long, accountIdStr: String): Int

    @Query("DELETE FROM ea_robot_events WHERE login = :accountId OR (login == 0 AND id = :accountIdStr)")
    suspend fun deleteEventsForAccount(accountId: Long, accountIdStr: String)

    @Query("DELETE FROM ea_robot_events")
    suspend fun clearAllEvents()
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE accountId = :accountId LIMIT 1")
    suspend fun getMetadata(accountId: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE accountId = :accountId LIMIT 1")
    fun getMetadataFlow(accountId: String): Flow<SyncMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE accountId = :accountId")
    suspend fun deleteMetadata(accountId: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun clearAllMetadata()
}
