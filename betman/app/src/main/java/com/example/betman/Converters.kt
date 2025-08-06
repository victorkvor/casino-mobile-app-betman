package com.example.betman

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import java.io.ByteArrayOutputStream
import java.util.Date

class Converters {

    // Convert from Date to Long (Unix timestamp in milliseconds)
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time // Return the time in milliseconds
    }

    // Convert from Long (Unix timestamp) to Date
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) } // Convert the timestamp back to a Date object
    }

    // Converts a Bitmap into a ByteArray
    @TypeConverter
    fun fromBitmap(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    // Converts a ByteArray back into a Bitmap when retrieving from the database
    @TypeConverter
    fun toBitmap(byteArray: ByteArray?): Bitmap? {
        if (byteArray == null || byteArray.isEmpty()) return null
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }
}
