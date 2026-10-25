package com.example.tracker.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "track_point")
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speedMps: Float,
    val mode: Int,
    val sessionId: Long = 0
)

@Entity(tableName = "track_session")
data class TrackSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long = 0
)

@Dao
interface TrackDao {
    @Insert
    suspend fun insert(point: TrackPoint)

    @Insert
    suspend fun insertSession(session: TrackSession): Long

    @Query("UPDATE track_session SET endedAt = :endedAt WHERE id = :id")
    suspend fun closeSession(id: Long, endedAt: Long)

    @Query("SELECT * FROM track_session ORDER BY startedAt DESC")
    suspend fun sessions(): List<TrackSession>

    @Query("SELECT * FROM track_session WHERE id = :id")
    suspend fun session(id: Long): TrackSession?

    @Query("SELECT * FROM track_session ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastSession(): TrackSession?

    @Query("SELECT * FROM track_point WHERE sessionId = :id ORDER BY timestamp")
    suspend fun pointsFor(id: Long): List<TrackPoint>

    @Query("SELECT * FROM track_point ORDER BY timestamp")
    suspend fun getAll(): List<TrackPoint>

    @Query("DELETE FROM track_point WHERE sessionId = :id")
    suspend fun deletePoints(id: Long)

    @Query("DELETE FROM track_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM track_point")
    suspend fun deleteAllPoints()
}

@Database(
    entities = [TrackPoint::class, TrackSession::class],
    version = 2,
    exportSchema = false
)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile private var INSTANCE: TrackDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_point ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS track_session (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "startedAt INTEGER NOT NULL, " +
                        "endedAt INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): TrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrackDatabase::class.java,
                    "track.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
