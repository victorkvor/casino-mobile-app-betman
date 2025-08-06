package com.example.betman

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class BetsAdapter(
    private var betsList: List<Bet>,                        // List of bets to show
    private val repository: CasinoRepository,               // Repository to fetch usernames
    private val lifecycleScope: LifecycleCoroutineScope     // Lifecycle-aware coroutine scope
) : RecyclerView.Adapter<BetsAdapter.BetViewHolder>() {

    // ViewHolder: Holds and binds data to each bet card
    inner class BetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Set up UI components
        private val dateTextView: TextView = itemView.findViewById(R.id.bets_date)
        private val gameImageView: ImageView = itemView.findViewById(R.id.bets_gameimage)
        private val usernameTextView: TextView = itemView.findViewById(R.id.bets_usernametext)
        private val balanceTextView: TextView = itemView.findViewById(R.id.bets_balancetext)
        private val backgroundCardView: View = itemView.findViewById(R.id.bets_bg)
        private var currentJob: Job? = null

        fun bind(bet: Bet) {
            // Cancel any previous coroutine to prevent UI overlapping
            currentJob?.cancel()

            // Fetch username asynchronously using coroutine
            currentJob = lifecycleScope.launch {
                val username = withContext(Dispatchers.IO) {
                    repository.getUserName(bet.userId)
                }
                usernameTextView.text = username ?: "Unknown"
            }

            // Format(DD MM YYYY HH:MM) and display bet date
            val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            dateTextView.text = dateFormat.format(bet.date)

            // Calculate balance: bet_result - initial_bet
            val balance = bet.betResult - bet.initialBet
            balanceTextView.text = if (balance > 0) {
                String.format(Locale.getDefault(), "+%d", balance) // Add '+' for positive wins
            } else {
                String.format(Locale.getDefault(), "%d", balance)
            }

            setCardBackground(balance)  // Green for positive balance, red for negative balance
            setGameImage(bet.game)      // Show corresponding game icon
        }

        // Set background color based on balance. balance < 0 = red. balance >= 0 = green.
        private fun setCardBackground(balance: Int) {
            val color = if (balance < 0) Color.rgb(212, 68, 68) else Color.rgb(68, 212, 68)
            backgroundCardView.setBackgroundColor(color)
        }

        // Load the correct game image based on game name
        private fun setGameImage(game: String) {
            val gameImageRes = when (game.lowercase(Locale.ROOT)) {
                "roulette" -> R.drawable.ruleta_icon
                "slots" -> R.drawable.slots_icon
                "crash" -> R.drawable.crash_icon
                "plinko" -> R.drawable.plinko_icon
                "blackjack" -> R.drawable.blackjack_icon
                "mines" -> R.drawable.mines_mine
                "dice" -> R.drawable.dice_icon
                "dragontower" -> R.drawable.dragon_tower_icon
                else -> R.drawable.bet_placeholder_icon // Default fallback icon
            }
            gameImageView.setImageResource(gameImageRes)
        }
    }

    // Inflate the layout for each item in the RecyclerView
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.bets, parent, false)
        return BetViewHolder(view)
    }

    // Bind data from betsList to each ViewHolder
    override fun onBindViewHolder(holder: BetViewHolder, position: Int) {
        holder.bind(betsList[position])
    }

    // Return number of items
    override fun getItemCount(): Int = betsList.size

    // Update the list and refresh the RecyclerViewW
    @SuppressLint("NotifyDataSetChanged")
    fun updateBets(newBets: List<Bet>) {
        betsList = newBets
        notifyDataSetChanged()
    }
}