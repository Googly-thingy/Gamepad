package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TvServerDao {
    @Query("SELECT * FROM tv_servers ORDER BY lastConnected DESC")
    fun getAllServers(): Flow<List<TvServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: TvServerEntity): Long

    @Update
    suspend fun updateServer(server: TvServerEntity)

    @Delete
    suspend fun deleteServer(server: TvServerEntity)

    @Query("SELECT * FROM tv_servers WHERE ipAddress = :ip LIMIT 1")
    suspend fun getServerByIp(ip: String): TvServerEntity?
}

@Dao
interface ControllerProfileDao {
    @Query("SELECT * FROM controller_profiles WHERE id = :id LIMIT 1")
    fun getProfile(id: String = "default"): Flow<ControllerProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ControllerProfileEntity)
}

@Database(
    entities = [TvServerEntity::class, ControllerProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tvServerDao(): TvServerDao
    abstract fun controllerProfileDao(): ControllerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "game_controller_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
