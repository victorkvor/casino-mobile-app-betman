package com.example.betman

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class DragonTower : Fragment() {

    // Difficulty levels for the game: determines how many invalid (skull) cells per row.
    enum class Difficulty(val invalidCount: Int) {
        EASY(1), MID(2), HARD(3)
    }

    // Views for the layout
    private lateinit var difficultySpinner: Spinner
    private lateinit var betAmountEditText: EditText
    private lateinit var profitEditText: EditText
    private lateinit var playButton: Button
    private lateinit var moneyText: TextView

    // Cell references: each row has 4 cells, which are divided into two lists:
    // One for the cell's background (regular cell or selected cell) and the other for the objects (egg or skull) that appear in them.
    private lateinit var cellBackgrounds: List<List<ImageView>>
    private lateinit var cellObjects: List<List<ImageView>>

    // Views for dragon and castle images
    private lateinit var dragonHead: ImageView
    private lateinit var dragonBody: ImageView
    private lateinit var castleTop: ImageView

    // Drawable resources for different states (adjust based on your assets)
    private val dragonHeadDefault = R.drawable.dragon_head_default
    private val dragonHeadWin = R.drawable.dragon_head_win
    private val dragonHeadLose = R.drawable.dragon_head_lose

    private val dragonBodyDefault = R.drawable.dragon_body_default
    private val dragonBodyWin = R.drawable.dragon_body_win
    private val dragonBodyLose = R.drawable.dragon_body_lose

    private val castleTopDefault = R.drawable.castle_top
    private val castleTopWin = R.drawable.castle_top_win

    // Tower configuration (6 rows, 4 columns)
    private val rows = 6
    private val cols = 4
    // towerConfig: an array for each row (true = valid cell/egg, false = invalid cell/skull)
    private lateinit var towerConfig: Array<BooleanArray>
    private var currentRow = 0

    // MediaPlayer for sound effects
    private var mediaPlayer: MediaPlayer? = null

    // User's ID and repository instance
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    // Game state variables
    private var currentBet = 10
    private var winnings = 0
    private var currentMult = 8
    private var currentMoney = 0

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): DragonTower {
            val fragment = DragonTower()
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
    @SuppressLint("DiscouragedApi")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout
        val view = inflater.inflate(R.layout.fragment_dragon_tower, container, false)

        // Initialize UI elements
        difficultySpinner = view.findViewById(R.id.dtower_difficultyspinner)
        betAmountEditText = view.findViewById(R.id.dtower_betamount)
        profitEditText = view.findViewById(R.id.dtower_earnings)
        playButton = view.findViewById(R.id.dtower_play)
        moneyText = view.findViewById(R.id.dtower_money)

        // Load the current money of the user
        loadMoney()

        // Set up the difficulty spinner with options "EASY", "MID", "HARD"
        val diffList = listOf("EASY", "MID", "HARD")
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_text, diffList)
        adapter.setDropDownViewResource(R.layout.spinner_text)
        difficultySpinner.adapter = adapter

        // Initialize cell backgrounds and objects (egg/skull) for each cell in the grid. dtower_c{row}_{col}_cell y dtower_c{row}_{col}_img)
        cellBackgrounds = (1..rows).map { row ->
            (1..cols).map { col ->
                view.findViewById(resources.getIdentifier("dtower_c${row}_${col}_cell", "id", requireContext().packageName))
            }
        }
        cellObjects = (1..rows).map { row ->
            (1..cols).map { col ->
                view.findViewById(resources.getIdentifier("dtower_c${row}_${col}_img", "id", requireContext().packageName))
            }
        }

        // Initialize the dragon and castle images
        dragonHead = view.findViewById(R.id.dtower_dragonhead)
        dragonBody = view.findViewById(R.id.dtower_dragonbody)
        castleTop = view.findViewById(R.id.dtower_castletop)

        // Set up the play button to start the game when clicked
        playButton.setOnClickListener {
            if (!validateBetAmount()) {
                return@setOnClickListener
            }
            startGame()
        }

        // Add a listener to the bet amount input field to validate and update it
        betAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateBetAmount() // Validate whenever the text is changed
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Set up the listener for difficulty changes
        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentMult = getMultiplier(difficultySpinner.selectedItem.toString())
                winnings = currentBet * currentMult
                profitEditText.setText(String.format(Locale.getDefault(), "%d", winnings))
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        return view
    }

    // Start the game: Set up the tower and initiate game logic
    private fun startGame() {
        // Disable inputs and UI elements to prevent interaction during the game
        difficultySpinner.isEnabled = false
        betAmountEditText.isEnabled = false
        playButton.isEnabled = false
        substractMoney(currentBet)

        // Reset variables and UI
        currentRow = 0
        resetCellBackgrounds()
        resetTowerUI()

        // Set default dragon and castle images
        dragonHead.setImageResource(dragonHeadDefault)
        dragonBody.setImageResource(dragonBodyDefault)
        castleTop.setImageResource(castleTopDefault)

        // Set up animations for the dragon
        setupDragonAnimations()

        // Get the difficulty level selected by the player
        val diff = when (difficultySpinner.selectedItem.toString()) {
            "EASY" -> Difficulty.EASY
            "MID" -> Difficulty.MID
            "HARD" -> Difficulty.HARD
            else -> Difficulty.EASY
        }

        // Generate the tower configuration based on the difficulty level
        towerConfig = Array(rows) { BooleanArray(cols) { true } }
        for (row in 0 until rows) {
            val invalidPositions = (0 until cols).shuffled().take(diff.invalidCount)
            for (col in invalidPositions) {
                towerConfig[row][col] = false
            }
        }

        // Enable the first row for selection
        enableRow(currentRow, true)
    }

    // Handle a cell being selected (clicked by the player)
    private fun onCellSelected(row: Int, col: Int) {
        if (row != currentRow) return  // Ignore if it's not the current row

        // Disable the row to prevent double clicks
        enableRow(currentRow, false)

        if (towerConfig[row][col]) {
            // Valid cell: show the egg
            setCellObject(row, col, R.drawable.egg)
            cellBackgrounds[row][col].setBackgroundResource(R.drawable.cells_successful)
            playSuccessEffect()                         // Play success sound effect
            fadeOutUnselectedCells(row)                 // Fade out unselected cells
            restorePreviousRow(row, col)

            currentRow++                                // Move to the next row
            if (currentRow >= rows) {
                onWin()                                 // Win the game
                saveBet(winnings)                       // Save the result
                // Reset UI and re-enable controls
                difficultySpinner.isEnabled = true
                betAmountEditText.isEnabled = true
                playButton.isEnabled = true
            } else {
                enableRow(currentRow, true)      // Enable the next row for selection
            }
        } else {
            // Invalid cell: show the skull and end the game
            playFailEffect()                            // Play fail sound effect
            setCellObject(row, col, R.drawable.skull)
            cellBackgrounds[row][col].setBackgroundResource(R.drawable.cells_failure)
            onLose()                                    // End the game
            saveBet(0)                         // Save the result (loss)
            difficultySpinner.isEnabled = true
            betAmountEditText.isEnabled = true
            playButton.isEnabled = true
        }
    }

    // Function to save the bet result to the database
    private fun saveBet(betResult: Int) {
        val bet = Bet(
            userId = userId,
            initialBet = currentBet,
            betResult = betResult,
            date = Date(),
            game = "DragonTower"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            repository.addBet(bet) // Store the bet result in the database
        }
    }

    // Function called when the player wins the game
    private fun onWin() {
        // Update dragon and castle images for the win state
        dragonHead.setImageResource(dragonHeadWin)
        dragonBody.setImageResource(dragonBodyWin)
        castleTop.setImageResource(castleTopWin)
        disableAllCells()                           // Disable all cells after winning
        playWinEffect()                             // Play win sound effect
        addMoney(winnings)                          // Add winnings to the user's balance
    }

    // Function called when the player loses the game
    private fun onLose() {
        // Update dragon images for the lose state
        dragonHead.setImageResource(dragonHeadLose)
        dragonBody.setImageResource(dragonBodyLose)

        // Reveal all skulls in unselected cells after a loss
        for (row in currentRow until rows) {
            for (col in 0 until cols) {
                if (!towerConfig[row][col]) {                  // If it's a skull cell
                    setCellObject(row, col, R.drawable.skull)  // Show skull
                    cellBackgrounds[row][col].setBackgroundResource(R.drawable.cells_failure)
                }
            }
        }

        // Disable all cells after the loss
        disableAllCells()
    }


    // Enable or disable cells in the current row for user interaction
    private fun enableRow(row: Int, enable: Boolean) {
        // Mark the current row as active by applying a specific background
        if (enable) markActiveRow(row)

        // Iterate through each column of the given row
        for (col in 0 until cols) {
            val bg = cellBackgrounds[row][col]                      // Reference to the cell background view
            if (enable) {
                bg.isClickable = true                               // Make the cell clickable
                bg.setOnClickListener { onCellSelected(row, col) }  // Assign an onClickListener to handle cell selection
            } else {
                bg.isClickable = false                              // Disable clicking if not enabled
                bg.setOnClickListener(null)
            }
        }
    }

    // Function to disable all cells in all rows, making them unclickable
    private fun disableAllCells() {
        // Iterate through all rows and disable them one by one
        for (row in 0 until rows) {
            enableRow(row, false)
        }
    }

    // Updates the image of a specific cell, either displaying an egg or a skull, based on the drawable resource passed
    private fun setCellObject(row: Int, col: Int, drawableRes: Int) {
        cellObjects[row][col].setImageResource(drawableRes)
        cellObjects[row][col].visibility = View.VISIBLE
    }

    // Resets the UI of the tower by hiding all the objects in the cells (egg or skull)
    private fun resetTowerUI() {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                cellObjects[row][col].visibility = View.INVISIBLE
            }
        }
    }

    // Marks the background of the active row by changing its appearance (for visual feedback)
    private fun markActiveRow(row: Int) {
        for (col in 0 until cols) {
            cellBackgrounds[row][col].setBackgroundResource(R.drawable.cells_select)
        }
    }

    // Resets all cell backgrounds to their default state (unhighlighted)
    private fun resetCellBackgrounds() {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                cellBackgrounds[row][col].setBackgroundResource(R.drawable.cells)
                cellBackgrounds[row][col].alpha = 1.0f
            }
        }
    }

    // Sets up animations for the dragon (both head and body) that make them move up and down
    private fun setupDragonAnimations() {
        // Animation for the dragon's body, moves it 20px downward and then back up
        val bodyAnimator = ObjectAnimator.ofFloat(dragonBody, "translationY", 0f, 20f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        bodyAnimator.start()

        // Animation for the dragon's head, moves it 20px upward and then back down
        val headAnimator = ObjectAnimator.ofFloat(dragonHead, "translationY", 0f, -20f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        headAnimator.start()
    }

    // Reduces the opacity of unselected cells in a row to 50% for visual feedback
    private fun fadeOutUnselectedCells(row: Int) {
        for (col in 0 until cols) {
            if (cellObjects[row][col].visibility == View.INVISIBLE) {
                cellBackgrounds[row][col].alpha = 0.5f  // Reduce opacidad al 50%
            }
        }
    }

    // Restores the background of previously selected cells in the row to their normal state.
    private fun restorePreviousRow(row: Int, selectedCol: Int) {
        for (col in 0 until cols) {
            if (col != selectedCol) {
                cellBackgrounds[row][col].setBackgroundResource(R.drawable.cells)  // Restaurar a estado normal
            }
        }
    }

    // Validates the bet amount entered by the user in the input field
    private fun validateBetAmount(): Boolean {
        val betText = betAmountEditText.text.toString()
        val bet = betText.toIntOrNull()

        if (bet == null || bet <= 0) {
            betAmountEditText.error = "Invalid bet amount"
            return false
        }

        if (bet > currentMoney) {
            betAmountEditText.error = "Insufficient balance"
            return false
        }

        currentBet = bet
        return true
    }

    // Gets the multiplier based on the selected difficulty level
    private fun getMultiplier(difficulty: String): Int {
        return when (difficulty) {
            "EASY" -> 8
            "MID" -> 80
            "HARD" -> 5000
            else -> 0  // Optionally handle invalid difficulty strings
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
    private fun playSuccessEffect() {
        // Initialize the MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.dtower_success)

        // Start the sound
        mediaPlayer?.start()

        // Release the MediaPlayer when done
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }

    // Function to play the sound effect
    private fun playFailEffect() {
        // Initialize the MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.dtower_fail)

        // Start the sound
        mediaPlayer?.start()

        // Release the MediaPlayer when done
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }

    // Function to play the sound effect
    private fun playWinEffect() {
        // Initialize the MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.dtower_win)

        // Start the sound
        mediaPlayer?.start()

        // Release the MediaPlayer when done
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }
}