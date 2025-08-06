package com.example.betman

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.cbrt

class Crash : Fragment(), CrashGameView.OnCrashListener {
    // UI components
    private lateinit var crashGameView: CrashGameView
    private lateinit var moneyText: TextView
    private lateinit var playButton: AppCompatButton
    private lateinit var betAmountEditText: EditText
    private lateinit var earningsEditText: EditText
    private lateinit var multiplierEditText: EditText
    private lateinit var winchanceEditText: EditText

    // Logic variables
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository
    private var currentBet = 0
    private var currentMultiplierTarget = 1.0f
    private var currentMoney = 0
    private var winnings = 0

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Crash {
            val fragment = Crash()
            val args = Bundle()
            args.putLong("USER_ID", userId) // Save userId into arguments
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Retrieve user ID from arguments
            userId = it.getLong("USER_ID", -1)
        }

        // Initialize repository with context
        repository = CasinoRepository(requireContext())
    }

    // Inflate the layout and setup UI bindings
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_crash, container, false)

        // Bind UI elements
        crashGameView = view.findViewById(R.id.crashGameView)
        betAmountEditText = view.findViewById(R.id.crash_betamount)
        earningsEditText = view.findViewById(R.id.crash_earnings)
        multiplierEditText = view.findViewById(R.id.crash_multtarget)
        winchanceEditText = view.findViewById(R.id.crash_winchance)
        playButton = view.findViewById(R.id.crash_play)
        moneyText = view.findViewById(R.id.crash_money)

        repository = CasinoRepository(requireContext())

        // Load user's money and set up listeners
        loadMoney()
        setupListeners()

        // Set callback for game result
        crashGameView.setOnCrashListener(this)

        return view
    }

    // Set up listeners for UI interactions
    private fun setupListeners() {
        // Monitor changes in bet and multiplier fields
        betAmountEditText.addTextChangedListener {
            validateBet()
            updatePlayButtonState()
            updateWinChanceAndEarnings()
        }

        multiplierEditText.addTextChangedListener { text ->
            val inputText = text.toString()

            // Check number format with optional decimals
            if (inputText.isNotEmpty()) {
                // if number not valid format
                if (!inputText.matches(Regex("^\\d+(\\.\\d{0,2})?$"))) {
                    multiplierEditText.error = "Invalid format"
                    updatePlayButtonState()
                    return@addTextChangedListener
                }

                // Two decimals maximum
                if (inputText.contains(".")) {
                    val parts = inputText.split(".")
                    if (parts.size > 2) { // Múltiples puntos
                        multiplierEditText.error = "Invalid format"
                    } else if (parts[1].length > 2) { // Más de 2 decimales
                        multiplierEditText.error = "Two decimals max"
                    } else {
                        multiplierEditText.error = null
                    }
                }

                // Verify numeric value
                val value = inputText.toFloatOrNull()
                if (value == null || value <= 1.0f) {
                    multiplierEditText.error = "Minimun 1.01"
                }
            }

            // Update state
            updatePlayButtonState()
            updateWinChanceAndEarnings()
        }

        // Play button triggers the game if inputs are valid
        playButton.setOnClickListener {
            if (validateBet()) {
                placeBet()
                crashGameView.startGame()
                betAmountEditText.isEnabled = false
                multiplierEditText.isEnabled = false
                playButton.isEnabled = false
            }
        }
    }

    // Validates the bet amount input
    private fun validateBet(): Boolean {
        val bet = betAmountEditText.text.toString().toIntOrNull()
        return when {
            bet == null -> {
                betAmountEditText.error = "Invalid bet"
                false
            }
            bet < 100 -> {
                betAmountEditText.error = "Minimum 100 coins"
                false
            }
            bet > currentMoney -> {
                betAmountEditText.error = "Not enough money"
                false
            }
            else -> {
                betAmountEditText.error = null // Clear error if valid
                true
            }
        }
    }

    // Enable play button only if inputs are valid
    private fun updatePlayButtonState() {
        // Enable the play button only when both bet and multiplier fields are filled
        val bet = betAmountEditText.text.toString().toIntOrNull()
        val multiplier = multiplierEditText.text.toString().toFloatOrNull()

        // Enable only if both fields are valid
        playButton.isEnabled = bet != null && bet >= 100 && multiplier != null && multiplier > 1
    }

    // Calculate and show the win chance and possible earnings
    @SuppressLint("SetTextI18n")
    private fun updateWinChanceAndEarnings() {
        // Calculate the win chance and update the earnings only if the multiplier is valid
        val bet = betAmountEditText.text.toString().toIntOrNull() ?: 0
        val multiplier = multiplierEditText.text.toString().toFloatOrNull() ?: 1.0f

        // Only perform calculation if multiplier is greater than 1
        if (multiplier <= 1) {
            // Set UI to show invalid input
            winchanceEditText.setText("Invalid")
            earningsEditText.setText("Invalid")
        } else {
            // Calculate earnings based on current bet and multiplier
            val earnings = calculateEarnings(bet, multiplier)

            // Update the UI elements with the new values
            winchanceEditText.setText(String.format(Locale.getDefault(),"%.2f %%", calculateWinChance(multiplier)))
            earningsEditText.setText(String.format(Locale.getDefault(), "%d", earnings))
        }
    }

    // Calculates the chance of winning based on the multiplier chosen by the user
    private fun calculateWinChance(target: Float): Double {
        val adjustedTarget = target.toDouble()
        var probability = 0.0

        // Range 1: Multiplier between 1.0x and 1.5x -> Base chance: 60%
        if (adjustedTarget <= 1.0) {
            probability += 0.60
        } else if (adjustedTarget <= 1.5) {
            probability += 0.60 * (1.5 - adjustedTarget) / 0.5
        }

        // Range 2: Multiplier between 1.5x and 3.0x -> Base chance: 35%
        if (adjustedTarget <= 1.5) {
            probability += 0.35
        } else if (adjustedTarget <= 3.0) {
            probability += 0.35 * (3.0 - adjustedTarget) / 1.5
        }

        // Range 3: Multiplier above 3.0x -> Base chance: 5%
        if (adjustedTarget <= 3.0) {
            probability += 0.05
        } else {
            val t = (adjustedTarget - 3.0) / 50.0
            if (t < 1.0) {
                val cubeRoot = cbrt(t)
                probability += 0.05 * (1.0 - cubeRoot)
            }
        }

        // Return win chance percentage, clamped between 0 and 100
        return (probability * 100).coerceIn(0.0, 100.0)
    }

    // Function to calculate the earnings
    private fun calculateEarnings(bet: Int, multiplier: Float): Int {
        // Calculate earnings based on bet and multiplier
        return (bet * multiplier).toInt()
    }

    // Function that saves the amount of the bet and the multiplier target, and also substracts the money betted
    private fun placeBet() {
        currentBet = betAmountEditText.text.toString().toInt()
        currentMultiplierTarget = multiplierEditText.text.toString().toFloat()
        substractMoney(currentBet)
    }

    // Called when the game ends (crashes) and a multiplier is reached
    override fun onCrash(multiplier: Float) {
        // Determine if player has won (reached or exceeded their target multiplier)
        val win = multiplier >= currentMultiplierTarget
        // If won, calculate winnings. else, set to 0
        winnings = if (win) (currentBet * currentMultiplierTarget).toInt() else 0

        // Handle win logic: play sound, add money to balance
        if (win) {
            playWinEffect()
            addMoney(winnings)
        }

        // Save the bet result in the database
        val betResult = Bet(
            userId = userId,
            initialBet = currentBet,
            betResult = winnings,
            date = Date(),
            game = "Crash"
        )

        // Store the result asynchronously in background
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addBet(betResult)
        }

        // Reset the error message after the game round ends
        multiplierEditText.error = null
        showResult(win, winnings)
        playButton.isEnabled = true
        betAmountEditText.isEnabled = true
        multiplierEditText.isEnabled = true
    }

    // Shows a Toast with win/loss result
    private fun showResult(win: Boolean, profit: Int) {
        val message = if (win) "¡YOU WON ${profit}!" else "¡YOU LOSE!"
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    // Function to load money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentMoney = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", currentMoney).replace(',', ' ') } // Format number with commas
        }
    }

    // Function to add money
    private fun addMoney(bet: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addMoney(userId, bet)
            launch(Dispatchers.Main) {
                loadMoney()
            }
        }
    }

    // Function to substract money
    private fun substractMoney(bet: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.subtractMoney(userId, bet)
            launch(Dispatchers.Main) {
                loadMoney()
            }
        }
    }

    // Plays the winning sound effect
    private fun playWinEffect() {
        MediaPlayer.create(context, R.raw.crash_win).apply {
            start()
            setOnCompletionListener { release() }
        }
    }
}