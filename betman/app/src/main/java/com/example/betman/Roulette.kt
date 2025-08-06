package com.example.betman

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class Roulette : Fragment(), RouletteGameView.OnSpinEndListener {
    // UI components
    private lateinit var redButton: AppCompatButton
    private lateinit var blackButton: AppCompatButton
    private lateinit var greenButton: AppCompatButton
    private lateinit var playButton: AppCompatButton
    private lateinit var rouletteGameView: RouletteGameView
    private lateinit var moneyText: TextView
    private lateinit var betAmountEditText: EditText
    private lateinit var earningsEditText: EditText

    // Game data
    private var selectedColor: String? = null
    private var isSpinning = false
    private var currentBet = 0
    private var winnings = 0
    private var currentMoney = 0

    // Media player variable
    private var mediaPlayer: MediaPlayer? = null

    // User data and repository
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Roulette {
            val fragment = Roulette()
            val args = Bundle()
            args.putLong("USER_ID", userId) // Save userId into arguments
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve user ID from arguments
        arguments?.let {
            userId = it.getLong("USER_ID", -1) // Retrieve ID
        }

        // Initialize repository with context
        repository = CasinoRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_roulette, container, false)

        redButton = view.findViewById(R.id.roulette_redbtn)
        blackButton = view.findViewById(R.id.roulette_blackbtn)
        greenButton = view.findViewById(R.id.roulette_greenbtn)
        playButton = view.findViewById(R.id.roulette_play)
        rouletteGameView = view.findViewById(R.id.rouletteGameView)
        moneyText = view.findViewById(R.id.roulette_money)
        betAmountEditText = view.findViewById(R.id.roulette_betamount)
        earningsEditText = view.findViewById(R.id.roulette_earnings)

        loadMoney()

        setupBetAmountListener()
        setupColorButtons()
        setupPlayButton()
        rouletteGameView.setOnSpinEndListener(this)

        return view
    }

    // Function to load money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentMoney = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", currentMoney).replace(',', ' ') } // Format number with commas
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

    // Function to add money
    private fun addMoney(bet: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addMoney(userId, bet)
            launch(Dispatchers.Main) {
                loadMoney()
            }
        }
    }

    // Sets up the listener for the bet amount edit text field to dynamically update earnings and play button state
    private fun setupBetAmountListener() {
        betAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateEarningsPreview()
                validatePlayButtonState()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // Function to enable/disable play button dynamically
    private fun validatePlayButtonState() {
        val betAmountText = betAmountEditText.text.toString().trim()
        val betAmount = betAmountText.toIntOrNull()

        when {
            betAmount == null || betAmount <= 0 -> {
                betAmountEditText.error = "Enter a valid bet"
            }
            betAmount > currentMoney -> {
                betAmountEditText.error = "Not enough money"
            }
            else -> {
                betAmountEditText.error = null
            }
        }
    }

    // Updates the earnings preview based on the selected color and bet amount
    private fun updateEarningsPreview() {
        currentBet = betAmountEditText.text.toString().toIntOrNull() ?: 0

        // If no color is selected, set earnings to zero
        winnings = when (selectedColor) {
            "red", "black" -> currentBet * 2  // Double if red or black
            "green" -> currentBet * 20  // x20 if green
            else -> 0  // No selection = zero earnings
        }

        earningsEditText.setText(String.format(Locale.getDefault(), "%d", winnings))
    }

    // Sets up listeners for color selection buttons (red, black, green)
    private fun setupColorButtons() {
        val buttons = listOf(redButton to "red", blackButton to "black", greenButton to "green")

        buttons.forEach { (button, color) ->
            button.setOnClickListener {
                selectedColor = color
                updateButtonStates(button)
                playButton.isEnabled = !isSpinning
                playButton.alpha = if (!isSpinning) 1.0f else 0.5f
            }
        }
    }

    // Updates the appearance of color buttons based on selection
    private fun updateButtonStates(selectedButton: AppCompatButton) {
        listOf(redButton, blackButton, greenButton).forEach { button ->
            if (button == selectedButton) {
                button.alpha = 1.0f
                button.isEnabled = false
            } else {
                button.alpha = 0.4f
                button.isEnabled = true
            }
        }

        updateEarningsPreview()
    }

    // Sets up the play button to trigger the spin when clicked
    private fun setupPlayButton() {
        playButton.isEnabled = true  // Always enabled
        playButton.alpha = 1.0f

        playButton.setOnClickListener {
            val betAmount = betAmountEditText.text.toString().toIntOrNull()

            if (betAmount == null || betAmount <= 0 || betAmount > currentMoney || selectedColor == null) {
                return@setOnClickListener // Do nothing if invalid bet
            }

            currentBet = betAmount
            substractMoney(currentBet)
            betAmountEditText.isEnabled = false
            isSpinning = true
            playButton.isEnabled = false // Temporarily disable during spin
            playButton.alpha = 0.5f
            rouletteGameView.startRoulette()
        }
    }

    // This method is called when the roulette spin ends, and it handles the result
    override fun onSpinEnd(winningNumber: Int) {
        activity?.runOnUiThread {
            isSpinning = false
            betAmountEditText.isEnabled = true
            val winningColor = determineWinningColor(winningNumber)
            handleGameResult(winningColor)
            resetColorSelection()
        }
    }

    // Determines the color of the winning number
    private fun determineWinningColor(number: Int): String = when (number) {
        0 -> "green"
        in setOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36) -> "red"
        else -> "black"
    }

    // Handles the game result, updates the user balance and UI based on win/loss
    private fun handleGameResult(winningColor: String) {
        val betResult = if (selectedColor == winningColor) winnings else 0
        val bet = Bet(
            userId = userId,
            initialBet = currentBet,
            betResult = betResult,
            date = Date(),
            game = "Roulette"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            repository.addBet(bet)
        }

        // If the user wins, play a win effect and add winnings
        if (selectedColor == winningColor) {
            playWinEffect()
            addMoney(winnings)
        } else {
            validatePlayButtonState()
        }
    }

    // Resets the color selection and button states for the next round
    private fun resetColorSelection() {
        selectedColor = null
        listOf(redButton, blackButton, greenButton).forEach {
            it.alpha = 1.0f
            it.isEnabled = true
        }
        playButton.isEnabled = true
        playButton.alpha = 1.0f
    }

    // Plays a sound effect when the user wins
    private fun playWinEffect() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, R.raw.roulette_win).apply {
            start()
            setOnCompletionListener { release() }
        }
    }
}