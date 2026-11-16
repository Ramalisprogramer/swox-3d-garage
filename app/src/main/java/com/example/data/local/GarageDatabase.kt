package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.model.VehicleClass
import com.example.model.VehicleEntity
import com.example.model.VehicleSummary
import kotlinx.coroutines.flow.Flow

data class VehicleSummaryRow(
    val id: String,
    val name: String,
    val brand: String,
    val vehicleClass: String,
    val passportCode: String,
    val motorWatts: Int,
    val topSpeedKmh: Double
) {
    fun toSummary(): VehicleSummary = VehicleSummary(
        id = id,
        name = name,
        brand = brand,
        vehicleClass = runCatching { VehicleClass.valueOf(vehicleClass) }.getOrDefault(VehicleClass.SCOOTER),
        passportCode = passportCode,
        motorWatts = motorWatts,
        topSpeedKmh = topSpeedKmh
    )
}

data class CategoryCountRow(
    val vehicleClass: String,
    val itemCount: Int
)

@Dao
interface VehicleDao {

    @Query(
        """
        SELECT id, name, brand, vehicleClass, passportCode, motorWatts, topSpeedKmh
        FROM vehicles
        WHERE (:category IS NULL OR vehicleClass = :category)
          AND (
            :query = ''
            OR name LIKE '%' || :query || '%'
            OR brand LIKE '%' || :query || '%'
            OR passportCode LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
          )
        ORDER BY passportCode ASC, name ASC
        LIMIT :limit
        """
    )
    fun observeFilteredSummaries(
        query: String,
        category: String?,
        limit: Int
    ): Flow<List<VehicleSummaryRow>>

    @Query(
        """
        SELECT * FROM vehicles
        WHERE (:category IS NULL OR vehicleClass = :category)
          AND (
            :query = ''
            OR name LIKE '%' || :query || '%'
            OR brand LIKE '%' || :query || '%'
            OR passportCode LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
          )
        ORDER BY passportCode ASC, name ASC
        LIMIT :limit
        """
    )
    fun observeFilteredVehicles(
        query: String,
        category: String?,
        limit: Int
    ): Flow<List<VehicleEntity>>

    @Query("SELECT vehicleClass, COUNT(*) AS itemCount FROM vehicles GROUP BY vehicleClass")
    fun observeCategoryCounts(): Flow<List<CategoryCountRow>>

    @Query("SELECT vehicleClass, COUNT(*) AS itemCount FROM vehicles GROUP BY vehicleClass")
    suspend fun getCategoryCountsOnce(): List<CategoryCountRow>

    @Query("SELECT COUNT(*) FROM vehicles")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun getCount(): Int

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(vehicles: List<VehicleEntity>)

    @Transaction
    suspend fun upsertAllChunked(vehicles: List<VehicleEntity>) {
        vehicles.chunked(500).forEach { batch ->
            upsertAll(batch)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vehicle: VehicleEntity)
}

@Database(
    entities = [VehicleEntity::class],
    version = 5,
    exportSchema = false
)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile
        private var INSTANCE: GarageDatabase? = null

        fun getInstance(context: Context): GarageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GarageDatabase::class.java,
                    "swox_3d_garage.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
