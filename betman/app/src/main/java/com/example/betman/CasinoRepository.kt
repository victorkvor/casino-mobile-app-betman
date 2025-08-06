package com.example.betman

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// Class to interact with the databases
class CasinoRepository(context: Context) {

    // Room database instance
    private val db = AppDatabase.getDatabase(context)

    // Access to User and Bet DAOs for querying the database
    private val userDao = db.userDao()
    private val betDao = db.betDao()

    // Get a user
    suspend fun getUserByUsername(username: String): User? {
        return userDao.getUserByUsername(username)
    }

    // Get username
    suspend fun getUserName(userId: Long): String? {
        return userDao.getUserName(userId)
    }

    // Insert a user
    suspend fun addUser(user: User): Long {
        return userDao.insertUser(user) // Returns the inserted user ID
    }

    // Retrieve money from user
    suspend fun getMoney(userId: Long): Int {
        return userDao.getMoney(userId)
    }

    // Add money from user
    suspend fun addMoney(userId: Long, amount: Int) {
        return userDao.addMoney(userId, amount)
    }

    // Substract money from user
    suspend fun subtractMoney(userId: Long, amount: Int) {
        return userDao.subtractMoney(userId, amount)
    }

    // Get user info
    suspend fun getUserById(userId: Long) = userDao.getUserById(userId)

    // Get richest users
    suspend fun getTopUsers() = userDao.getTop8UsersByMoney()

    // Register a bet
    suspend fun addBet(bet: Bet) = betDao.insertBet(bet)

    // Get bet count of a user
    suspend fun getUserBetCount(userId: Long) = betDao.getBetCountByUser(userId)

    // Get most played game
    suspend fun getMostPlayedGame(userId: Long) = betDao.getMostPlayedGameByUser(userId)

    // Get total winnings
    suspend fun getTotalWinnings(userId: Long) = betDao.getTotalWinningsByUser(userId)

    // Get latest bets
    fun latestBets(): LiveData<List<Bet>> {
        return betDao.getAllBetsOrderedByDate()
    }

    // Get user ranking ordered by money
    suspend fun getUserRanking(userId: Long): Int {
        // Run database query in IO dispatcher to avoid blocking main thread
        val userRankings = withContext(Dispatchers.IO) {
            userDao.getUsersMoneyRank()
        }

        userRankings.forEachIndexed { index, ranking ->
            if (ranking.userId == userId) {
                return index + 1 // Return the 1-based rank
            }
        }
        return -1 // Return -1 if the user is not found
    }

    // Deletes a user along with all of their bets
    suspend fun deleteUserWithBets(userId: Long) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            runBlocking {
                betDao.deleteBetsByUserId(userId)
                userDao.deleteUserById(userId)
            }
        }
    }

    // Updates profile image for a specific user
    suspend fun updateProfileImage(userId: Long, image: Bitmap) {
        userDao.updateProfileImage(userId, image)
    }
}