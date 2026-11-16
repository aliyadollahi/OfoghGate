package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CallDirection
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val displayName: String,
    val direction: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val networkModeUsed: String = ""
)

@Entity(tableName = "quick_extensions")
data class QuickExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val extension: String,
    val label: String,
    val department: String = ""
)

@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_records ORDER BY timestampMs DESC LIMIT 100")
    fun observeRecentCalls(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(record: CallRecordEntity)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteCall(id: Long)

    @Query("DELETE FROM call_records")
    suspend fun clearAllCalls()
}

@Dao
interface QuickExtensionDao {
    @Query("SELECT * FROM quick_extensions ORDER BY label ASC")
    fun observeQuickExtensions(): Flow<List<QuickExtensionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtension(item: QuickExtensionEntity)

    @Query("DELETE FROM quick_extensions WHERE id = :id")
    suspend fun deleteExtension(id: Long)
}

@Database(
    entities = [CallRecordEntity::class, QuickExtensionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SoftphoneDatabase : RoomDatabase() {
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun quickExtensionDao(): QuickExtensionDao

    companion object {
        @Volatile
        private var INSTANCE: SoftphoneDatabase? = null

        fun getInstance(context: Context): SoftphoneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SoftphoneDatabase::class.java,
                    "issabel_gate_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class CallRepository(
    private val callHistoryDao: CallHistoryDao,
    private val quickExtensionDao: QuickExtensionDao
) {
    val recentCalls: Flow<List<CallRecordEntity>> = callHistoryDao.observeRecentCalls()
    val quickExtensions: Flow<List<QuickExtensionEntity>> = quickExtensionDao.observeQuickExtensions()

    suspend fun logCall(
        number: String,
        displayName: String,
        direction: CallDirection,
        durationSeconds: Long,
        networkModeUsed: String
    ) {
        callHistoryDao.insertCall(
            CallRecordEntity(
                number = number,
                displayName = displayName.ifBlank { number },
                direction = direction.name,
                timestampMs = System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                networkModeUsed = networkModeUsed
            )
        )
    }

    suspend fun deleteCall(id: Long) = callHistoryDao.deleteCall(id)
    suspend fun clearCalls() = callHistoryDao.clearAllCalls()

    suspend fun saveQuickExtension(extension: String, label: String, department: String) {
        if (extension.isBlank()) return
        quickExtensionDao.upsertExtension(
            QuickExtensionEntity(
                extension = extension.trim(),
                label = label.ifBlank { "داخلی ${extension.trim()}" },
                department = department.trim()
            )
        )
    }

    suspend fun deleteQuickExtension(id: Long) = quickExtensionDao.deleteExtension(id)
}
