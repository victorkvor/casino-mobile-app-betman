package com.example.betman

import android.graphics.Bitmap
import androidx.room.*

// The DAO (Data Access Object) for interacting with the 'users' table in the Room database
@Dao
interface UserDao {
    // Query to get the username for a user by id
    @Query("SELECT username FROM users WHERE id_user = :userId LIMIT 1")
    suspend fun getUserName(userId: Long): String?

    // Query to fetch a User object by their username
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    // Insert a new user
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User): Long

    // Update an existing user by their id
    @Update
    suspend fun updateUser(user: User)

    // Query to fetch the money balance for a user by their id
    @Query("SELECT money FROM users WHERE id_user = :userId")
    suspend fun getMoney(userId: Long): Int

    // Query to add a certain amount to a user's money balance by their id
    @Query("UPDATE users SET money = money + :amount WHERE id_user = :userId")
    suspend fun addMoney(userId: Long, amount: Int)

    // Query to subtract a certain amount from a user's money balance by their id
    @Query("UPDATE users SET money = money - :amount WHERE id_user = :userId")
    suspend fun subtractMoney(userId: Long, amount: Int)

    // Query to fetch a User object by their id
    @Query("SELECT * FROM users WHERE id_user = :userId")
    suspend fun getUserById(userId: Long): User?

    // Query to get the top 8 users based on their money, in descending order
    @Query("SELECT * FROM users ORDER BY money DESC LIMIT 8")
    suspend fun getTop8UsersByMoney(): List<User>

    // Query to delete a user by their id
    @Query("DELETE FROM users WHERE id_user = :userId")
    suspend fun deleteUserById(userId: Long)

    // Query to update a user's profile image by their id
    @Query("UPDATE users SET profile_image = :image WHERE id_user = :userId")
    suspend fun updateProfileImage(userId: Long, image: Bitmap)

    // Query to fetch the ranking of users based on their money, sorted in descending order
    @Query("""
        SELECT id_user, money
        FROM users 
        GROUP BY id_user
        ORDER BY money DESC
    """)
    fun getUsersMoneyRank(): List<UserRanking>
}