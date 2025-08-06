package com.example.betman

import androidx.room.ColumnInfo

// Data class representing the ranking of a user based on their money balance
data class UserRanking(
    @ColumnInfo(name = "id_user") val userId: Long,
    @ColumnInfo(name = "money") val money: Int
)

