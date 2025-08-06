package com.example.betman

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class Dice : Fragment() {
    // UI components
    private lateinit var seekBar: SeekBar
    private lateinit var cubo: TextView
    private lateinit var playButton: AppCompatButton
    private lateinit var slidervalue: TextView
    private lateinit var moneyText: TextView
    private lateinit var betAmountEditText: EditText
    private lateinit var earningsEditText: EditText
    private lateinit var multiplierEditText: EditText
    private lateinit var winchanceEditText: EditText

    // MediaPlayer for sound effects
    private var mediaPlayer: MediaPlayer? = null

    // Variables for user data and repository access
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    // Logic variables for bets
    private var currentBet = 100
    private var currentMult = 2.0f
    private var winnings = 200
    private var currentMoney = 0

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Dice {
            val fragment = Dice()
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
        val view = inflater.inflate(R.layout.fragment_dice, container, false)

        // Bind UI elements
        seekBar = view.findViewById(R.id.dice_slider)
        cubo = view.findViewById(R.id.dice_cubo)
        slidervalue = view.findViewById(R.id.dice_slidervalue)
        moneyText = view.findViewById(R.id.dice_money)
        betAmountEditText = view.findViewById(R.id.dice_betamount)
        earningsEditText = view.findViewById(R.id.dice_earnings)
        multiplierEditText = view.findViewById(R.id.dice_mult)
        winchanceEditText = view.findViewById(R.id.dice_winchance)
        playButton = view.findViewById(R.id.dice_play)

        // Load the user's current balance from database
        loadMoney()

        // Set listeners
        playButton.setOnClickListener {
            if (!validateBetAmount()) {
                return@setOnClickListener
            }

            val betAmount = betAmountEditText.text.toString().toInt()
            substractMoney(betAmount)
            val resultado = generarNumeroAleatorio()
            playDiceEffect()
            mostrarRombo(resultado)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                slidervalue.text = "$progress"
                currentMult = (1.0f + (progress / 100.0f * 2.0f))
                currentMult = (Math.round(currentMult * 100) / 100.0f)
                winnings = (currentBet * currentMult).toInt()
                multiplierEditText.setText(String.format(Locale.getDefault(), "%.2f", currentMult))
                earningsEditText.setText(String.format(Locale.getDefault(), "%d", winnings))
                winchanceEditText.setText(String.format(Locale.getDefault(), "%d %%", (100 - progress)))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        betAmountEditText.setOnEditorActionListener { _, _, _ ->
            validateBetAmount()
            true
        }

        return view
    }

    // Function to check if the bet amount is valid
    @SuppressLint("SetTextI18n")
    private fun validateBetAmount(): Boolean {
        val betAmount = betAmountEditText.text.toString().toIntOrNull()

        return when {
            betAmount == null -> {
                betAmountEditText.error = "Enter a valid bet"
                earningsEditText.setText("Invalid")
                playButton.isEnabled = false
                false
            }
            betAmount < 100 -> {
                betAmountEditText.error = "Bet must be at least 100"
                earningsEditText.setText("Invalid")
                playButton.isEnabled = false
                false
            }
            betAmount > currentMoney -> {
                betAmountEditText.error = "Not enough money"
                earningsEditText.setText("Invalid")
                playButton.isEnabled = false
                false
            }
            else -> {
                currentBet = betAmount
                winnings = (currentBet * currentMult).toInt()
                earningsEditText.setText(String.format(Locale.getDefault(), "%d", winnings))
                playButton.isEnabled = true
                true
            }
        }
    }

    // Function to generate a random number
    private fun generarNumeroAleatorio(): Int {
        return Random.nextInt(101)
    }

    // Function to show a rhombus where the random number ended
    private fun mostrarRombo(resultado: Int) {
        cubo.visibility = View.VISIBLE
        cubo.text = String.format(Locale.getDefault(), "%d", resultado)

        val posicion = (seekBar.width - seekBar.paddingLeft - seekBar.paddingRight) * resultado / 100
        val seekBarX = seekBar.x + seekBar.paddingLeft + posicion - cubo.width / 2

        cubo.animate()
            .x(seekBarX)
            .setDuration(300)
            .start()

        val valorSlider = seekBar.progress
        var auxbetresult = 0
        if (valorSlider > resultado) {
            cubo.setTextColor(Color.parseColor("#E9113C"))
        } else {
            auxbetresult = winnings
            cubo.setTextColor(Color.parseColor("#00E701"))
            addMoney(winnings)
        }
        val betResult = Bet(
            userId = userId,
            initialBet = currentBet,
            betResult = auxbetresult,
            date = Date(),
            game = "Dice"
        )
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addBet(betResult)
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

    // Function to play a dice roll sound effect
    private fun playDiceEffect() {
        mediaPlayer = MediaPlayer.create(context, R.raw.dice_roll)
        mediaPlayer?.start()
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }
}
