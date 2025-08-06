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

class Dashboard : Fragment() {
    // UI components
    private lateinit var casinoButton: ImageButton
    private lateinit var betsButton: ImageButton
    private lateinit var rankingButton: ImageButton
    private lateinit var bmarketButton: ImageButton
    private lateinit var profileButton: ImageButton
    private lateinit var moneyText: TextView

    // Logic variables
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Dashboard {
            val fragment = Dashboard()
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
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        // Bind UI elements
        casinoButton = view.findViewById(R.id.dashboard_casinobtn)
        betsButton = view.findViewById(R.id.dashboard_bettingsbtn)
        rankingButton = view.findViewById(R.id.dashboard_rankingbtn)
        bmarketButton = view.findViewById(R.id.dashboard_bmarketbtn)
        profileButton = view.findViewById(R.id.dashboard_profilebtn)
        moneyText = view.findViewById(R.id.dashboard_money)

        // Load user's money and set up listeners
        loadMoney()

        casinoButton.setOnClickListener {
            // Load casino fragment
            val casinoFragment = Casino.newInstance(userId)
            loadFragment(casinoFragment)
        }

        betsButton.setOnClickListener {
            // Load bets fragment
            val betsFragment = Bets.newInstance(userId)
            loadFragment(betsFragment)
        }

        rankingButton.setOnClickListener {
            // Load ranking fragment
            val rankingFragment = Ranking.newInstance(userId)
            loadFragment(rankingFragment)
        }

        bmarketButton.setOnClickListener {
            // Load market fragment
            val bmarketFragment = BlackMarket.newInstance(userId)
            loadFragment(bmarketFragment)
        }

        profileButton.setOnClickListener {
            // Load profile fragment
            val profileFragment = Profile.newInstance(userId)
            loadFragment(profileFragment)
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

    // Function to load a fragment
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