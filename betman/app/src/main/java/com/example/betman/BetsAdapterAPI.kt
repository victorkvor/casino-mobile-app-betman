package com.example.betman

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class BetsAdapterAPI(private val bets: List<BetAPI>) : RecyclerView.Adapter<BetsAdapterAPI.BetViewHolder>() {

    // ViewHolder class responsible for displaying each bet item from the API
    class BetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Set up UI components
        private val dateTextView: TextView = itemView.findViewById(R.id.bets_date)
        private val gameImageView: ImageView = itemView.findViewById(R.id.bets_gameimage)
        private val usernameTextView: TextView = itemView.findViewById(R.id.bets_usernametext)
        private val balanceTextView: TextView = itemView.findViewById(R.id.bets_balancetext)
        private val backgroundCardView: View = itemView.findViewById(R.id.bets_bg)

        @SuppressLint("SetTextI18n")
        fun bind(bet: BetAPI) {
            // Set username directly from the API
            usernameTextView.text = bet.username

            // Calculate balance = betResult - initialBet (used in stats too)
            val balance = bet.betResult - bet.initialBet
            balanceTextView.text = if (balance > 0) {
                String.format(Locale.getDefault(), "+%d", balance) // Add '+' if positive
            } else {
                String.format(Locale.getDefault(), "%d", balance)
            }

            // Parse date from API format ("M/d/yyyy") and format it to readable form
            val inputFormat = SimpleDateFormat("M/d/yyyy", Locale.US)               // Format received from API
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) // Format to display

            try {
                val parsedDate = inputFormat.parse(bet.date) // Convert string to Date
                dateTextView.text = parsedDate?.let { outputFormat.format(it) } ?: "Invalid Date"
            } catch (e: Exception) {
                dateTextView.text = "Invalid Date" // Fallback if parsing fails
            }

            // Set color based on whether balance is positive or negative
            setCardBackground(bet.betResult - bet.initialBet)
            // Set corresponding image for API-exclusive games
            setGameImage(bet.game)
        }

        // Set background color based on balance. balance < 0 = red. balance >= 0 = green.
        private fun setCardBackground(balance: Int) {
            val color = if (balance < 0) Color.rgb(212, 68, 68) else Color.rgb(68, 212, 68)
            backgroundCardView.setBackgroundColor(color)
        }

        // Load the correct game image based on game name
        private fun setGameImage(game: String) {
            val gameImageRes = when (game.lowercase(Locale.ROOT)) {
                "illegal races" -> R.drawable.illegal_races_icon
                "animal fights" -> R.drawable.animal_fights_icon
                "underground fights" -> R.drawable.underground_fights_icon
                else -> R.drawable.russian_roulette_icon // Default image for unknown games
            }
            gameImageView.setImageResource(gameImageRes)
        }
    }

    // Inflate item layout
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bets, parent, false)
        return BetViewHolder(view)
    }

    // Bind data to ViewHolder
    override fun onBindViewHolder(holder: BetViewHolder, position: Int) {
        holder.bind(bets[position])
    }

    // Number of items to display
    override fun getItemCount(): Int = bets.size
}