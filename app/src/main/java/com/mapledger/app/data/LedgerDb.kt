package com.mapledger.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun expenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE placeId = :placeId ORDER BY createdAt DESC")
    fun expensesAt(placeId: String): Flow<List<Expense>>

    // SQLite returns the bare columns from the row holding MAX(createdAt): latest name/category win.
    @Query(
        """SELECT placeId, placeName, placeType, category, lat, lng, COUNT(*) AS visits,
           SUM(amountCents) AS totalCents, MAX(createdAt) AS lastAt
           FROM expenses GROUP BY placeId"""
    )
    fun placeSummaries(): Flow<List<PlaceSummary>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE createdAt >= :from")
    fun totalSince(from: Long): Flow<Long>

    @Insert
    suspend fun insert(e: Expense): Long

    @Delete
    suspend fun delete(e: Expense)

    @Upsert
    suspend fun upsertOverride(o: PlaceOverride)

    @Query("SELECT * FROM place_overrides")
    fun overrides(): Flow<List<PlaceOverride>>
}

@Database(entities = [Expense::class, PlaceOverride::class], version = 1, exportSchema = true)
abstract class LedgerDb : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        @Volatile private var instance: LedgerDb? = null
        fun get(ctx: Context): LedgerDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, LedgerDb::class.java, "ledger.db")
                .build().also { instance = it }
        }
    }
}
