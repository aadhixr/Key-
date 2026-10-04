package com.example.mykeyboard

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "keystrokes")
data class KeystrokeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val timestamp: Long,
    val packageName: String?
)

@Dao
interface KeystrokeDao {
    @Insert
    suspend fun insert(log: KeystrokeLog)
}

@Database(entities = [KeystrokeLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keystrokeDao(): KeystrokeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "keystrokes.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
