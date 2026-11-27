package com.mapledger.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "expenses", indices = [Index("placeId"), Index("createdAt")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val category: String,
    val placeId: String,
    val placeName: String,
    val placeType: String,
    val lat: Double,
    val lng: Double,
    val createdAt: Long,
)

/** The category a user chose for a place; reused next time they record there. */
@Entity(tableName = "place_overrides")
data class PlaceOverride(@PrimaryKey val placeId: String, val category: String)

data class PlaceSummary(
    val placeId: String,
    val placeName: String,
    val placeType: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val visits: Int,
    val totalCents: Long,
    val lastAt: Long,
)
