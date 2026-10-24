package com.example.tracker.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Entity(tableName = "track_point")
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speedMps: Float,
    val mode: Int
)

@Dao
interface TrackDao {
    @Insert
    suspend fun insert(point: TrackPoint)

    @Query("SELECT * FROM track_point ORDER BY timestamp")
    suspend fun getAll(): List<TrackPoint>
}

@Database(entities = [TrackPoint::class], version = 1, exportSchema = false)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile private var INSTANCE: TrackDatabase? = null

        fun getInstance(context: Context): TrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrackDatabase::class.java,
                    "track.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}