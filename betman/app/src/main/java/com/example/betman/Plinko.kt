package com.example.betman

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class Plinko : Fragment(), PlinkoBoardView.OnPlinkoResultListener {
    // UI components
    private lateinit var plinkoBoard: PlinkoBoardView
    private lateinit var moneyText: TextView
    private lateinit var playButton: AppCompatButton
    private lateinit var betAmountEditText: EditText
    private lateinit var winAmountEditText: EditText

    // Game-related data
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository
    private var currentBet = 0
    private var currentMoney = 0
    private var winnings = 0

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Plinko {
            return Plinko().apply {
                arguments = Bundle().apply {
                    putLong("USER_ID", userId)  // Save userId into arguments
                }
            }
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_plinko, container, false)

        // Initialize UI elements
        plinkoBoard = view.findViewById(R.id.plinkoBoard)
        playButton = view.findViewById(R.id.plinko_play)
        betAmountEditText = view.findViewById(R.id.plinko_betamount)
        winAmountEditText = view.findViewById(R.id.plinko_earnings)
        moneyText = view.findViewById(R.id.plinko_money)

        // Load money from database
        loadMoney()

        // Setup listeners
        plinkoBoard.setOnPlinkoResultListener(this)
        setupListeners()

        return view
    }

    // Sets up listeners for the bet field and play button
    private fun setupListeners() {
        // Validate bet on text change
        betAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateBet()
                updatePlayButtonState()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Play button click starts the game if bet is valid
        playButton.setOnClickListener {
            if (validateBet()) {
                placeBet()
                plinkoBoard.dropBall()
                disableInputs()
            }
        }
    }

    // Validates the current bet and displays appropriate error messages
    private fun validateBet(): Boolean {
        val betStr = betAmountEditText.text.toString()
        val bet = betStr.toIntOrNull()

        return when {
            betStr.isEmpty() -> {
                betAmountEditText.error = "Insert a value"
                false
            }
            bet == null -> {
                betAmountEditText.error = "Invalid bet"
                false
            }
            bet < 10 -> {
                betAmountEditText.error = "Minimum 10 coins"
                false
            }
            bet > currentMoney -> {
                betAmountEditText.error = "Not enough money"
                false
            }
            else -> {
                betAmountEditText.error = null
                true
            }
        }
    }

    // Enables or disables play button based on valid bet range
    private fun updatePlayButtonState() {
        playButton.isEnabled = betAmountEditText.text.toString().toIntOrNull()?.let { it in 10..currentMoney } ?: false
    }

    // Deducts the current bet and updates the UI
    private fun placeBet() {
        currentBet = betAmountEditText.text.toString().toInt()
        subtractMoney(currentBet)
        winAmountEditText.setText("???") // Show "???" while ball is falling
    }

    // Callback for when the Plinko board returns a result
    override fun onPlinkoResult(multiplier: Float) {
        winnings = (currentBet * multiplier).toInt() // Calculate total win
        addMoney(winnings) // Add winnings to account
        enableInputs() // Re-enable inputs after game ends

        // Save the bet in DB for statistics
        val betResult = Bet(
            userId = userId,
            initialBet = currentBet,
            betResult = winnings,
            date = Date(),
            game = "Plinko"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            repository.addBet(betResult)
        }

        // Update the UI with winnings on the main thread
        lifecycleScope.launch(Dispatchers.Main) {
            winAmountEditText.setText(String.format(Locale.getDefault(), "%d", winnings))
        }
    }

    // Disables user inputs during Plinko animation
    private fun disableInputs() {
        betAmountEditText.isEnabled = false
        playButton.isEnabled = false
    }

    // Enables user inputs after animation ends
    private fun enableInputs() {
        betAmountEditText.isEnabled = true
        playButton.isEnabled = true
    }

    // Function to load money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentMoney = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", currentMoney).replace(',', ' ') } // Format number with commas
        }
    }

    // Function to substract money
    private fun subtractMoney(amount: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.subtractMoney(userId, amount)
            loadMoney()
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
}
