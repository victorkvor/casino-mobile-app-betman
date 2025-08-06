package com.example.betman

// Data model used for API communication for external casino
data class BetAPI(
    val userId: Int,           // ID of the user who placed the bet
    val username: String,      // The username
    val initialBet: Int,       // The amount of the initial bet
    val betResult: Int,        // The result or winnings from the bet
    val date: String,          // The date the bet was placed
    val game: String           // Game name
)