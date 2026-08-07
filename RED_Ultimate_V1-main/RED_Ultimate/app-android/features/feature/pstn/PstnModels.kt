package com.red.feature.pstn

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pstn_call_logs")
data class PstnCallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val timestamp: Long,
    val duration: Long = 0,
    val direction: String, // INCOMING, OUTGOING, MISSED
    val status: String // DIALING, RINGING, ACTIVE, ENDED
)

@Dao
interface PstnDao {
    @Insert
    suspend fun insertLog(log: PstnCallLog)

    @Query("SELECT * FROM pstn_call_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<PstnCallLog>>
}

@Database(entities = [PstnCallLog::class], version = 1)
abstract class PstnDatabase : RoomDatabase() {
    abstract fun pstnDao(): PstnDao
}
