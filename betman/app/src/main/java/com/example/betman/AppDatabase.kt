package com.example.betman

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Define the Room database with two entities: User and Bet
// The version is set to 1, and a custom TypeConverter is used for unsupported types
@Database(entities = [User::class, Bet::class], version = 1)
@TypeConverters(Converters::class) // Register the converters
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun betDao(): BetDao

    companion object {
        // The INSTANCE is marked as @Volatile to ensure visibility across threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Returns a singleton instance of the database
        fun getDatabase(context: Context): AppDatabase {
            // If INSTANCE is null, create the database in a thread-safe way
            return INSTANCE ?: synchronized(this) {
                // Create the database instance using Room's builder
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Use the application context to avoid leaking activities
                    AppDatabase::class.java,    // Specify the database class
                    "gotham_casino_db"    // Name of the database file
                )
                    // Add fallbackToDestructiveMigration() for handling schema changes without migrations
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}