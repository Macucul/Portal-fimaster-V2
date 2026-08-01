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
