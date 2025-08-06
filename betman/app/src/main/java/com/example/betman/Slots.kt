package com.example.betman

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class Slots : Fragment() {
    // UI components
    private lateinit var slot1: ImageView
    private lateinit var slot2: ImageView
    private lateinit var slot3: ImageView
    private lateinit var spinButton: Button
    private lateinit var moneyText: TextView
    private lateinit var betAmountEditText: EditText
    private lateinit var resultTextView: TextView

    // Mediaplayers
    private lateinit var spinSound: MediaPlayer
    private lateinit var twoKindSound: MediaPlayer
    private lateinit var threeMatchSound: MediaPlayer

    // List of drawable symbols for the slot machine
    private val symbols = listOf(
        R.drawable.symbol_1,
        R.drawable.symbol_2,
        R.drawable.symbol_3,
        R.drawable.symbol_4,
        R.drawable.symbol_5,
        R.drawable.jackpot
    )

    // User data and repository
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    // Game data
    private var currentMoney = 0
    private var currentBet = 0

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Slots {
            val fragment = Slots()
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_slots, container, false)

        // UI components
        slot1 = view.findViewById(R.id.slot1)
        slot2 = view.findViewById(R.id.slot2)
        slot3 = view.findViewById(R.id.slot3)
        spinButton = view.findViewById(R.id.slots_play)
        betAmountEditText = view.findViewById(R.id.slots_betamount)
        resultTextView = view.findViewById(R.id.slots_result)
        moneyText = view.findViewById(R.id.slots_money)

        // Load money balance of the user
        loadMoney()

        // Initialize sound effects
        spinSound = MediaPlayer.create(context, R.raw.slots_spin)
        twoKindSound = MediaPlayer.create(context, R.raw.slots_2kind)
        threeMatchSound = MediaPlayer.create(context, R.raw.slots_jackpot)

        // Handle click on spin button
        spinButton.setOnClickListener {
            if (!validateBetAmount()) {
                return@setOnClickListener
            } else {
                betAmountEditText.isEnabled = false
                substractMoney(currentBet)
                spinSlots()
                betAmountEditText.error = null // Clear previous error
            }
        }

        // TextWatcher to enable/disable button based on input
        betAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateBetAmount()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return view
    }

    // Function to validate the input bet amount and enable/disable button accordingly
    private fun validateBetAmount(): Boolean {
        val betAmount = betAmountEditText.text.toString().toIntOrNull()

        return if (betAmount == null || betAmount <= 0) {
            betAmountEditText.error = "Insert a valid bet"
            spinButton.isEnabled = false
            false
        } else if (betAmount > currentMoney) {
            betAmountEditText.error = "Not enough money"
            spinButton.isEnabled = false
            false
        } else {
            currentBet = betAmount
            betAmountEditText.error = null
            spinButton.isEnabled = true
            true
        }
    }

    // Function that handles the main logic for spinning the slot machine
    private fun spinSlots() {
        spinButton.isEnabled = false
        resultTextView.text = ""

        // Play spin sound
        if (!spinSound.isPlaying) {
            spinSound.start()
        }

        val slots = listOf(slot1, slot2, slot3)

        // Initial random symbols shown during animation
        val initialSymbols = List(3) { symbols.random() }

        // Final symbols after animation ends
        val finalResults = List(3) { symbols.random() }

        // Show initial symbols
        slots.forEachIndexed { index, slot ->
            slot.setImageResource(initialSymbols[index])
        }

        // Create animation for each slot
        val animators = slots.mapIndexed { index, slot ->
            createSlotAnimator(slot, index * 300L)
        }

        // Combine all animations into one AnimatorSet
        val animatorSet = AnimatorSet().apply {
            duration = 2000L
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    slot1.setImageResource(finalResults[0])
                    slot2.setImageResource(finalResults[1])
                    slot3.setImageResource(finalResults[2])

                    // Get result and multiplier
                    val result = checkSlotResult(finalResults)
                    resultTextView.text = result.first  // Display the result message

                    val moneyToAdd = result.second * currentBet  // Calculate the amount to add

                    // Add money based on the result
                    if (moneyToAdd > 0) {
                        addMoney(moneyToAdd)
                    }

                    val bet = Bet(
                        userId = userId,
                        initialBet = currentBet,
                        betResult = moneyToAdd,
                        date = Date(),
                        game = "Slots"
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        repository.addBet(bet)
                    }

                    // Stop spin sound
                    spinSound.pause()
                    spinSound.seekTo(0)

                    // Determine which sound effect to play and wait for it to finish
                    val soundToPlay = when (finalResults.distinct().size) {
                        1 -> threeMatchSound
                        2 -> twoKindSound
                        else -> null
                    }

                    soundToPlay?.let { sound ->
                        sound.start()
                        sound.setOnCompletionListener {
                            spinButton.isEnabled = true
                            betAmountEditText.isEnabled = true
                        }
                    } ?: run {
                        spinButton.isEnabled = true
                        betAmountEditText.isEnabled = true

                    }
                }
            })
        }
        animatorSet.start()
    }

    // Function to create a ValueAnimator that simulates the spinning slot
    private fun createSlotAnimator(slot: ImageView, delay: Long): ValueAnimator {
        return ValueAnimator.ofInt(0, 30).apply {
            this.startDelay = delay
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                val symbolIndex = (progress % symbols.size)
                slot.setImageResource(symbols[symbolIndex])
            }
        }
    }

    // Check the result of the spin and return a message and multiplier
    private fun checkSlotResult(symbols: List<Int>): Pair<String, Int> {
        val jackpot = R.drawable.jackpot
        val secondBest = setOf(R.drawable.symbol_1, R.drawable.symbol_2)
        val frequencyMap = symbols.groupingBy { it }.eachCount()

        return when {
            // Case: All 3 symbols match
            frequencyMap.values.any { it == 3 } -> {
                val symbol = frequencyMap.entries.first { it.value == 3 }.key
                when (symbol) {
                    jackpot -> "¡JACKPOT! x500" to 500
                    in secondBest -> "¡THREE OF A KIND! x50" to 50
                    else -> "¡THREE OF A KIND! x20" to 20
                }
            }

            // Case: Only 2 symbols match
            frequencyMap.values.any { it == 2 } -> {
                val symbol = frequencyMap.entries.first { it.value == 2 }.key
                when (symbol) {
                    jackpot -> "¡TWO OF A KIND! x40" to 40
                    in secondBest -> "¡TWO OF A KIND! x5" to 5
                    else -> "¡TWO OF A KIND! x1" to 1
                }
            }
            // Case: No match
            else -> "¡TRY AGAIN!" to 0
        }
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

    // Release MediaPlayer resources when the view is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        spinSound.release()
        twoKindSound.release()
        threeMatchSound.release()
    }
}
