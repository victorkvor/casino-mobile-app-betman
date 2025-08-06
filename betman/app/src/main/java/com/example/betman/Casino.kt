package com.example.betman

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class Casino : Fragment() {
    // UI Components
    private lateinit var rouletteButton: ImageButton
    private lateinit var slotsButton: ImageButton
    private lateinit var crashButton: ImageButton
    private lateinit var plinkoButton: ImageButton
    private lateinit var blackjackButton: ImageButton
    private lateinit var minesButton: ImageButton
    private lateinit var diceButton: ImageButton
    private lateinit var dtowerButton: ImageButton
    private lateinit var moneyText: TextView

    // Variables to store user ID and data repository
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Casino {
            val fragment = Casino()
            val args = Bundle()
            args.putLong("USER_ID", userId) // Save userId into arguments
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Retrieve the userId passed as an argument to this fragment
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
        val view = inflater.inflate(R.layout.fragment_casino, container, false)

        // Initialize UI elements
        rouletteButton = view.findViewById(R.id.casino_roulettebtn)
        slotsButton = view.findViewById(R.id.casino_slotsbtn)
        crashButton = view.findViewById(R.id.casino_crashbtn)
        plinkoButton = view.findViewById(R.id.casino_plinkobtn)
        blackjackButton = view.findViewById(R.id.casino_blackjackbtn)
        minesButton = view.findViewById(R.id.casino_minesbtn)
        diceButton = view.findViewById(R.id.casino_dicebtn)
        dtowerButton = view.findViewById(R.id.casino_dragontowerbtn)
        moneyText = view.findViewById(R.id.casino_money)

        // Load money when the fragment is created
        loadMoney()

        // Set up listeners for each button
        rouletteButton.setOnClickListener {
            // Open Roulette Fragment
            val rouletteFragment = Roulette.newInstance(userId)
            loadFragment(rouletteFragment)
        }

        slotsButton.setOnClickListener {
            // Open Slots Fragment
            val slotsFragment = Slots.newInstance(userId)
            loadFragment(slotsFragment)
        }

        crashButton.setOnClickListener {
            // Open Crash Fragment
            val crashFragment = Crash.newInstance(userId)
            loadFragment(crashFragment)
        }

        plinkoButton.setOnClickListener {
            // Open Plinko Fragment
            val plinkoFragment = Plinko.newInstance(userId)
            loadFragment(plinkoFragment)
        }

        blackjackButton.setOnClickListener {
            // Open Blackjack Fragment
            val blackjackFragment = Blackjack.newInstance(userId)
            loadFragment(blackjackFragment)
        }

        minesButton.setOnClickListener {
            // Open Mines Fragment
            val minesFragment = Mines.newInstance(userId)
            loadFragment(minesFragment)
        }

        diceButton.setOnClickListener {
            // Open Dice Fragment
            val diceFragment = Dice.newInstance(userId)
            loadFragment(diceFragment)
        }

        dtowerButton.setOnClickListener {
            // Open Dragon Tower Fragment
            val dtowerFragment = DragonTower.newInstance(userId)
            loadFragment(dtowerFragment)
        }

        return view
    }

    // Function to load money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            val money = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", money).replace(',', ' ') } // Format number with commas
        }
    }

    // Function to load a new fragment with animation
    private fun loadFragment(fragment: Fragment) {
        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.pop_in,  // Enter animation (when opening a fragment)
                R.anim.pop_out, // Exit animation (when replacing the fragment)
                R.anim.pop_in,  // Pop Enter (when coming back)
                R.anim.pop_out  // Pop Exit (when going back)
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // Allows returning with the back button
            .commit()
    }
}