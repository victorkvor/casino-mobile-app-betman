package com.example.betman

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class Ranking : Fragment() {
    // Views for top 3 rankings (views related to rank 1-3)
    private lateinit var rank1Views: List<View>
    private lateinit var rank2Views: List<View>
    private lateinit var rank3Views: List<View>
    private lateinit var moneyText: TextView

    // Containers for rankings 4-8 (to display additional ranks dynamically)
    private val rankContainers = arrayOfNulls<LinearLayout>(5)

    // Game data
    private var currentMoney = 0

    // // User data and repository
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Ranking {
            val fragment = Ranking()
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ranking, container, false)
        moneyText = view.findViewById(R.id.rank_money)

        loadMoney() // Load the user's current money and display it
        setupRankViews(view) // Initialize all views for the rankings (top 3 and 4-8)
        loadRankingData() // Fetch and display the ranking data (top users)

        return view
    }

    // Function to set up the views for rankings 1-3 and containers for rankings 4-8
    private fun setupRankViews(view: View) {
        // Set up views for rank 1-3
        rank1Views = listOf(
            view.findViewById(R.id.rank_1img),
            view.findViewById(R.id.rank_1crown),
            view.findViewById(R.id.rank_1name),
            view.findViewById(R.id.rank_1money)
        )

        rank2Views = listOf(
            view.findViewById(R.id.rank_2img),
            view.findViewById(R.id.rank_2crown),
            view.findViewById(R.id.rank_2name),
            view.findViewById(R.id.rank_2money)
        )

        rank3Views = listOf(
            view.findViewById(R.id.rank_3img),
            view.findViewById(R.id.rank_3crown),
            view.findViewById(R.id.rank_3name),
            view.findViewById(R.id.rank_3money)
        )

        // Set up containers for ranks 4-8 (these are dynamically populated)
        rankContainers[0] = view.findViewById(R.id.rank_4container)
        rankContainers[1] = view.findViewById(R.id.rank_5container)
        rankContainers[2] = view.findViewById(R.id.rank_6container)
        rankContainers[3] = view.findViewById(R.id.rank_7container)
        rankContainers[4] = view.findViewById(R.id.rank_8container)
    }

    // Function that fetches the top users
    private fun loadRankingData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val users = repository.getTopUsers()

            launch(Dispatchers.Main) {
                updateUI(users)
            }
        }
    }

    // Updates the UI with the fetched ranking data
    private fun updateUI(users: List<User>) {
        // Update the top 3 rankings with the data for users 1-3
        updateTopRank(users.getOrNull(0), rank1Views)
        updateTopRank(users.getOrNull(1), rank2Views)
        updateTopRank(users.getOrNull(2), rank3Views)

        /// Update rankings 4-8 dynamically
        for (i in 3..7) { // i represents original user list index (users 4-8)
            val containerIndex = i - 3 // array index (0-4)
            val user = users.getOrNull(i)

            rankContainers[containerIndex]?.let { container ->
                if (user != null) {
                    container.visibility = View.VISIBLE

                    // Get view IDs based on rank position
                    val (nameId, moneyId, imgId) = when (i + 1) {
                        4 -> Triple(R.id.rank_4name, R.id.rank_4money, R.id.rank_4img)
                        5 -> Triple(R.id.rank_5name, R.id.rank_5money, R.id.rank_5img)
                        6 -> Triple(R.id.rank_6name, R.id.rank_6money, R.id.rank_6img)
                        7 -> Triple(R.id.rank_7name, R.id.rank_7money, R.id.rank_7img)
                        8 -> Triple(R.id.rank_8name, R.id.rank_8money, R.id.rank_8img)
                        else -> throw IllegalStateException("Invalid rank position")
                    }

                    // Update views
                    container.findViewById<TextView>(nameId).text = user.username
                    container.findViewById<TextView>(moneyId).text = String.format(Locale.getDefault(), "%,d", user.money).replace(',', ' ')

                    // Set bitmap directly from database
                    val imageView = container.findViewById<ImageView>(imgId)
                    user.profileImage?.let {
                        imageView.setImageBitmap(it)
                    } ?: run {
                        imageView.setImageResource(R.drawable.icon_user) // Fallback
                    }
                } else {
                    container.visibility = View.GONE
                }
            }
        }
    }

    // Function that updates top rank (1, 2, or 3) views with user data
    private fun updateTopRank(user: User?, views: List<View>) {
        val visibility = if (user != null) View.VISIBLE else View.GONE
        views.forEach { it.visibility = visibility }

        if (user != null) {
            // Update text views
            (views[2] as TextView).text = user.username
            (views[3] as TextView).text = String.format(Locale.getDefault(), "%,d", user.money).replace(',', ' ')

            // Update profile image
            val imageView = views[0] as ImageView
            user.profileImage?.let {
                imageView.setImageBitmap(it)
            } ?: run {
                imageView.setImageResource(R.drawable.icon_user)
            }
        }
    }

    // Function to load money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentMoney = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", currentMoney).replace(',', ' ') } // Format number with commas
        }
    }
}