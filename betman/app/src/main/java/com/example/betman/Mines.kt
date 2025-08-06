package com.example.betman

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class Mines : Fragment() {
    // Grid dimensions
    private val rows = 5
    private val cols = 5

    // Array of cell IDs corresponding to layout resources
    private val cellIds = arrayOf(
        intArrayOf(R.id.cell_0_0, R.id.cell_0_1, R.id.cell_0_2, R.id.cell_0_3, R.id.cell_0_4),
        intArrayOf(R.id.cell_1_0, R.id.cell_1_1, R.id.cell_1_2, R.id.cell_1_3, R.id.cell_1_4),
        intArrayOf(R.id.cell_2_0, R.id.cell_2_1, R.id.cell_2_2, R.id.cell_2_3, R.id.cell_2_4),
        intArrayOf(R.id.cell_3_0, R.id.cell_3_1, R.id.cell_3_2, R.id.cell_3_3, R.id.cell_3_4),
        intArrayOf(R.id.cell_4_0, R.id.cell_4_1, R.id.cell_4_2, R.id.cell_4_3, R.id.cell_4_4)
    )

    // Game state variables
    private var mines = mutableSetOf<Pair<Int, Int>>()
    private var revealedCells = mutableSetOf<Pair<Int, Int>>()
    private var isGameOver = true
    private var currentBet = 100
    private var currentMines = 1
    private var cellReward = 5
    private var currentMoney = 0
    private var winnings = 0

    // UI elements
    private lateinit var playButton: AppCompatButton
    private lateinit var cashoutButton: AppCompatButton
    private lateinit var mineSpinner: Spinner
    private lateinit var winningsEditText: EditText
    private lateinit var betAmountEditText: EditText
    private lateinit var moneyText: TextView

    // MediaPlayer for sound effects
    private var mediaPlayer: MediaPlayer? = null

    // User ID and database repository
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Mines {
            return Mines().apply {
                arguments = Bundle().apply {
                    putLong("USER_ID", userId) // Save userId into arguments
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
        val view = inflater.inflate(R.layout.fragment_mines, container, false)

        // Initialize UI elements
        playButton = view.findViewById(R.id.mines_play)
        cashoutButton = view.findViewById(R.id.mines_cashout)
        mineSpinner = view.findViewById(R.id.mines_minesspinner)
        winningsEditText = view.findViewById(R.id.mines_earnings)
        betAmountEditText = view.findViewById(R.id.mines_betamount)
        moneyText = view.findViewById(R.id.mines_money)

        // Load money from database
        loadMoney()

        // Setup dropdown and cells
        setupSpinner()
        setupCells(view)

        // Set up play button logic
        playButton.setOnClickListener {
            if (validateBetAmount()) {
                startGame(view)
            }
        }

        // Set up cashout button
        cashoutButton.setOnClickListener { cashOut() }

        // Add text watcher for validating the bet
        betAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateBetAmount()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Set up the spinner for choosing mine count
        mineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentMines = mineSpinner.selectedItem.toString().toInt()
                cellReward = kotlin.math.ceil(currentBet * (currentMines / (25.0 - currentMines))*3.0).toInt()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        return view
    }

    // Validates the bet amount and enables/disables play button accordingly
    private fun validateBetAmount(): Boolean {
        val betText = betAmountEditText.text.toString()
        val betValue = betText.toIntOrNull()

        if (betValue == null || betValue <= 0) {
            betAmountEditText.error = "Invalid bet"
            playButton.isEnabled = false
            return false
        } else if (betValue < 10){
            betAmountEditText.error = "Bet should be over 10"
            playButton.isEnabled = false
            return false

        } else if (betValue > currentMoney) {
            betAmountEditText.error = "Not enough money"
            playButton.isEnabled = false
            return false
        } else {
            currentBet = betValue
            betAmountEditText.error = null
            cellReward = kotlin.math.ceil(currentBet * (currentMines / (25.0 - currentMines))*3.0).toInt()
            playButton.isEnabled = true
            return true
        }
    }

    // Initialize spinner with possible mine counts
    private fun setupSpinner() {
        val mineOptions = listOf(1, 3, 5, 8, 12, 15, 20, 24)
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_text, mineOptions)
        adapter.setDropDownViewResource(R.layout.spinner_text)
        mineSpinner.adapter = adapter
    }

    // Start new game
    private fun startGame(view: View) {
        if (!isGameOver) return     // Prevents starting a new game if one is already running

        substractMoney(currentBet) // Deduct the current bet from the user's money
        isGameOver = false // Marks the game as active
        revealedCells.clear() // Clear any previously revealed cells
        winnings = 0
        winningsEditText.setText(String.format(Locale.getDefault(), "%d", winnings)) // Reset winnings display
        playButton.isEnabled = false // Disable the Play button during game
        mineSpinner.isEnabled = false // Lock the mine selection spinner
        betAmountEditText.isEnabled = false // Lock the bet input field

        showMessage("¡The match has started!")

        val betText =  betAmountEditText.text.toString()
        currentBet = if (betText.isNotEmpty()) betText.toInt() else 0
        val minesCount = mineSpinner.selectedItem.toString().toInt()
        generateMines(minesCount)

        resetCells(view)
    }

    // Function to generate mines with random positions
    private fun generateMines(count: Int) {
        mines.clear()
        while (mines.size < count) {
            val position = Pair((0 until rows).random(), (0 until cols).random())
            mines.add(position)
        }
    }

    // Function to set up cells with click listeners
    private fun setupCells(view: View) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = view.findViewById<ImageView>(cellIds[r][c])
                cell.setOnClickListener { onCellClicked(r, c, cell) }
            }
        }
    }

    // Function to reset cells with click listeners
    private fun resetCells(view: View) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = view.findViewById<ImageView>(cellIds[r][c])
                cell?.apply {
                    setImageDrawable(null)
                    setBackgroundResource(R.drawable.border_minesnoncheck)
                }
            }
        }
        revealedCells.clear()
    }

    // Function for cash out the winnings and end the game
    private fun cashOut() {
        if (!isGameOver && winnings > 0) {
            showMessage("¡YOU WON $winnings coins!")
            playButton.isEnabled = true
            mineSpinner.isEnabled = true // Enable spinner
            betAmountEditText.isEnabled = true
            saveBet(winnings) // Save the winning bet in history
            addMoney(winnings) // Add the winnings to the user's balance
            showAllMines() // Reveal all mine locations
            isGameOver = true
        }
    }

    // Handle cell clicked
    private fun onCellClicked(row: Int, col: Int, cell: ImageView) {
        // Do nothing if the game is over or if this cell has already been revealed
        if (isGameOver || revealedCells.contains(Pair(row, col))) return

        // Animate cell rotation
        val rotateAnimator = ObjectAnimator.ofFloat(cell, "rotationY", 0f, 180f)
        rotateAnimator.duration = 400  // Duration of the rotation in milliseconds
        rotateAnimator.interpolator = DecelerateInterpolator() // Smooth deceleration
        rotateAnimator.start()

        // Listen for end of rotation animation before revealing cell content
        rotateAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)

                // Delay the next actions slightly to improve UI after the rotation
                if (mines.contains(Pair(row, col))) {
                    // If the clicked cell contains a mine, trigger loss sequence
                    cell.postDelayed({
                        playMineEffect() // Play explosion sound

                        // Show explosion image and red border background
                        cell.setImageResource(R.drawable.mines_explosion)
                        cell.setBackgroundResource(R.drawable.border_minesnoncheckbomb)

                        // Fade out the explosion image to transition into mine image
                        val fadeOutAnimator = ObjectAnimator.ofFloat(cell, "alpha", 1f, 0f)
                        fadeOutAnimator.duration = 500 // Fade out duration
                        fadeOutAnimator.start()

                        fadeOutAnimator.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                // After fading out, replace with static mine image
                                cell.setImageResource(R.drawable.mines_mine)

                                // Fade in the mine image for visual polish
                                val fadeInAnimator = ObjectAnimator.ofFloat(cell, "alpha", 0f, 1f)
                                fadeInAnimator.duration = 200
                                fadeInAnimator.start()

                                // Reveal all mines on the board
                                showAllMines()

                                // Change border to indicate confirmed mine
                                cell.setBackgroundResource(R.drawable.border_minescheckbomb)
                            }
                        })
                    }, 300) // Delay image transition slightly to follow rotation
                    playButton.isEnabled = true
                    mineSpinner.isEnabled = true // Enable sponner after loss

                    // Game ended
                    betAmountEditText.isEnabled = true
                    showMessage("¡YOU LOSE! Play again.")
                    saveBet(0)
                    isGameOver = true
                } else {
                    // If the clicked cell is safe, play money animation/sound
                    playMoneyEffect()
                    cell.setImageResource(R.drawable.mines_money) // Show coin reward image
                    cell.setBackgroundResource(R.drawable.border_minescheck)  // Safe cell border

                    revealedCells.add(Pair(row, col)) // Track this cell as revealed
                    winnings += cellReward // Increase winnings

                    winningsEditText.setText(String.format(Locale.getDefault(), "%d", winnings))
                }
            }
        })
    }

    // Reveals all mine locations on the grid
    private fun showAllMines() {
        for (mine in mines) {
            val (r, c) = mine
            val cell = view?.findViewById<ImageView>(cellIds[r][c])

            // Set the image to a mine icon and the border
            cell?.setImageResource(R.drawable.mines_mine)
            cell?.setBackgroundResource(R.drawable.border_minesnoncheckbomb)
        }
    }

    // Displays a toast message to the user
    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Function to play the sound effect
    private fun playMoneyEffect() {
        // Initialize the MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.mines_coin)

        // Start the sound
        mediaPlayer?.start()

        // Release the MediaPlayer when done
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
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

    // Function to play the sound effect
    private fun playMineEffect() {
        // Initialize the MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.mines_mine)

        // Start the sound
        mediaPlayer?.start()

        // Release the MediaPlayer when done
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }

    // Save a bet result in the database
    private fun saveBet(betResult: Int) {
        val bet = Bet(
            userId = userId,
            initialBet = currentBet,
            betResult = betResult,
            date = Date(),
            game = "Mines"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            repository.addBet(bet)
        }
    }
}