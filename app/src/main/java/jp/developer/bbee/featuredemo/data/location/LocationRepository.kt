package jp.developer.bbee.featuredemo.data.location

import jp.developer.bbee.featuredemo.data.db.LocationPointDao
import jp.developer.bbee.featuredemo.data.db.LocationPointEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val dao: LocationPointDao,
) {
    fun getAvailableDates(): Flow<List<String>> = dao.getAvailableDates()

    fun getPointsForDate(date: String): Flow<List<LocationPointEntity>> =
        dao.getPointsForDate(date)

    suspend fun savePoint(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        dao.insertPoint(
            LocationPointEntity(
                latitude = latitude,
                longitude = longitude,
                timestamp = now,
                date = date,
            )
        )
    }
}
