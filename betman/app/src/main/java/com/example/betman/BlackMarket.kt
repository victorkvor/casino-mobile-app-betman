package com.example.betman

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Random

// Fragment representing the Black Market screen where the user can buy chips
class BlackMarket : Fragment() {
    // UI components
    private lateinit var moneyText: TextView
    private lateinit var chips1: LinearLayout
    private lateinit var chips2: LinearLayout
    private lateinit var chips3: LinearLayout
    private lateinit var chips4: LinearLayout
    private lateinit var chips5: LinearLayout
    private lateinit var chips6: LinearLayout

    // Variables for user data and repository access
    private var currentMoney = 0
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    // MediaPlayer for playing coin sound effects
    private var mediaPlayer: MediaPlayer? = null  // MediaPlayer instance

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): BlackMarket {
            val fragment = BlackMarket()
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
        val view = inflater.inflate(R.layout.fragment_black_market, container, false)

        // Bind UI elements
        moneyText = view.findViewById(R.id.bmarket_money)
        chips1 = view.findViewById(R.id.chips1)
        chips2 = view.findViewById(R.id.chips2)
        chips3 = view.findViewById(R.id.chips3)
        chips4 = view.findViewById(R.id.chips4)
        chips5 = view.findViewById(R.id.chips5)
        chips6 = view.findViewById(R.id.chips6)

        // Set click listeners for each chip layout to add specific amounts of money
        chips1.setOnClickListener { addMoney(2000) }
        chips2.setOnClickListener { addMoney(5000) }
        chips3.setOnClickListener { addMoney(10000) }
        chips4.setOnClickListener { addMoney(20000) }
        chips5.setOnClickListener { addMoney(100000) }
        chips6.setOnClickListener { addMoney(200000) }

        // Load the user's current balance from database
        loadMoney()

        return view
    }

    // Function to load money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentMoney = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", currentMoney).replace(',', ' ') } // Format number with commas
        }
    }

    // Function to add money
    private fun addMoney(amount: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addMoney(userId, amount)
            launch(Dispatchers.Main) {
                loadMoney()
                playCoinSound()
                showCoinsAddedDialog(amount)
            }
        }
    }

    // Shows a temporary dialog that visually indicates coins being added with animations
    private fun showCoinsAddedDialog(amount: Int) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.moneyadded_dialog, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        // Display the amount added with a "+" prefix
        val totalMoneyText = dialogView.findViewById<TextView>(R.id.totalMoneyText)
        totalMoneyText.text = String.format(Locale.getDefault(), "+%d", amount)

        // IDs of all the animated chip image views
        val chipIds = arrayOf(
            R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4,
            R.id.chip5, R.id.chip6, R.id.chip7, R.id.chip8
        )

        // Animate each chip
        chipIds.forEach { chipId ->
            val chip = dialogView.findViewById<ImageView>(chipId)
            chip.visibility = View.VISIBLE
            animateChip(chip, dialogView.width, dialogView.height)
        }

        // Dismiss after animation
        dialogView.postDelayed({ dialog.dismiss() }, 2000)
    }

    // Chip animation function with random movement
    private fun animateChip(chip: ImageView, maxWidth: Int, maxHeight: Int) {
        val random = Random()
        // Ensure safe bounds
        val safeMaxWidth = maxWidth.coerceAtLeast(100)
        val safeMaxHeight = maxHeight.coerceAtLeast(100)

        // Initial position
        val initialY = -chip.height.toFloat() * 2
        val targetY = (safeMaxHeight * 0.6f * random.nextFloat())

        // Initial configuration
        chip.alpha = 0f
        chip.translationX = (random.nextInt(safeMaxWidth) - (safeMaxWidth / 2)).toFloat()
        chip.translationY = initialY
        chip.scaleX = 0.5f
        chip.scaleY = 0.5f

        // Chip animation
        chip.animate()
            .setInterpolator(BounceInterpolator()) // Usa el interpolador de Android
            .alpha(1f)
            .translationY(targetY)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1200)
            .start()
    }

    // Plays a coin sound effect using MediaPlayer
    private fun playCoinSound() {
        mediaPlayer?.release() // Release previous instance if exists
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.add_coins)
        mediaPlayer?.start()
    }

    // Clean up resources when the fragment is destroyed
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release() // Release MediaPlayer when fragment is destroyed
        mediaPlayer = null
    }
}