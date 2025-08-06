package com.example.betman

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BetDao {
    // Inserts a bet into the database. If a conflict occurs (e.g., same ID), it replaces the existing entry
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBet(bet: Bet)

    // Returns the total number of bets made by a specific user
    @Query("SELECT COUNT(*) FROM bets WHERE user_id = :userId")
    suspend fun getBetCountByUser(userId: Long): Int

    // Returns the most frequently played game by the user
    @Query("SELECT game FROM bets WHERE user_id = :userId GROUP BY game ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostPlayedGameByUser(userId: Long): String?

    // Calculates total profit or loss by summing (bet_result - initial_bet) for all bets by the user
    @Query("SELECT SUM(bet_result - initial_bet) FROM bets WHERE user_id = :userId")
    suspend fun getTotalWinningsByUser(userId: Long): Int?

    // Retrieves all bets, ordered by date in descending order (most recent first)
    // Uses LiveData so UI can observe updates automatically
    @Query("SELECT * FROM bets ORDER BY date DESC")
    fun getAllBetsOrderedByDate(): LiveData<List<Bet>>

    // Deletes all bets associated with a specific user ID
    @Query("DELETE FROM bets WHERE user_Id = :userId")
    suspend fun deleteBetsByUserId(userId: Long)
}