package com.example.betman

import android.os.Parcelable
import androidx.room.*
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(
    tableName = "bets",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id_user"],
        childColumns = ["user_id"],
        onDelete = ForeignKey.CASCADE // Delete bets if user is deleted
    )],
    indices = [Index(value = ["user_id"])] // Improves query performance
)
data class Bet(
    @PrimaryKey(autoGenerate = true) val betid: Long = 0,   // Auto-generated pk
    @ColumnInfo(name = "user_id") val userId: Long,         // Fk to associate bet with a user
    @ColumnInfo(name = "initial_bet") val initialBet: Int,  // The amount of the initial bet
    @ColumnInfo(name = "bet_result") val betResult: Int,    // The result or winnings from the bet
    @ColumnInfo(name = "date") val date: Date,              // Date the bet was placed
    @ColumnInfo(name = "game") val game: String             // The game on which the bet was placed
) : Parcelable // Makes the class parcelable so it can be passed in bundles or intents
