package jp.developer.bbee.featuredemo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LocationPointEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationPointDao(): LocationPointDao
}
