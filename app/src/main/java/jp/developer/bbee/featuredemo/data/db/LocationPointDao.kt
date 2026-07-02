package jp.developer.bbee.featuredemo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {

    @Query("SELECT DISTINCT date FROM location_points ORDER BY date DESC")
    fun getAvailableDates(): Flow<List<String>>

    @Query("SELECT * FROM location_points WHERE date = :date ORDER BY timestamp ASC")
    fun getPointsForDate(date: String): Flow<List<LocationPointEntity>>

    @Insert
    suspend fun insertPoint(point: LocationPointEntity)
}
