package com.example.betman

import android.graphics.Bitmap
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import kotlinx.parcelize.Parcelize

// Enable Parcelable implementation using Kotlin Parcelize plugin
// Room entity representing the 'users' table in the local database
@Parcelize
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]          // Ensures username is unique
)
data class User(
    @PrimaryKey(autoGenerate = true) val id_user: Long = 0,         // Efficient primary key
    @ColumnInfo(name = "username") val username: String,            // Unique username
    @ColumnInfo(name = "password") val password: String,            // Password for the user
    @ColumnInfo(name = "money") val money: Int,                     // Balance of the user
    @ColumnInfo(name = "profile_image") val profileImage: Bitmap?,  // Stored as ByteArray
    @ColumnInfo(name = "country_code") val countryCode: String      // ISO country code
) : Parcelable // Allows the object to be passed between Android components

