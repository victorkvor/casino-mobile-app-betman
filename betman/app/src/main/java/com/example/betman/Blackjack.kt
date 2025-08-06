package com.example.betman

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class Blackjack : Fragment() {
    // UI components
    private lateinit var moneyText: TextView
    private lateinit var betAmountEditText: EditText

    // Card graphics and logic
    private lateinit var cardBack: Drawable
    private lateinit var cardValues: Map<String, Int>
    private lateinit var deck: MutableList<String>
    private lateinit var playerCards: MutableList<String>
    private lateinit var dealerCards: MutableList<String>
    private lateinit var playerScoreText: TextView
    private lateinit var dealerScoreText: TextView

    // ImageViews for displaying cards
    private lateinit var casinoCards: List<ImageView>
    private lateinit var playerCardsViews: List<ImageView>

    // Game control buttons
    private lateinit var hitButton: Button
    private lateinit var standButton: Button
    private lateinit var playButton: Button

    // Game state variables
    private var gameInProgress = false
    private val dealDelay = 300L
    private var mediaPlayer: MediaPlayer? = null
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    // Money and betting state
    private var currentBet = 0
    private var currentMoney = 0
    private var winnings = 0

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Blackjack {
            val fragment = Blackjack()
            val args = Bundle()
            args.putLong("USER_ID", userId)
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

        // Initialize repository
        repository = CasinoRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate Layout
        val view = inflater.inflate(R.layout.fragment_blackjack, container, false)

        // Initialize UI elements
        playButton = view.findViewById(R.id.blackjack_play)
        hitButton = view.findViewById(R.id.blackjack_hit)
        standButton = view.findViewById(R.id.blackjack_stand)
        betAmountEditText = view.findViewById(R.id.blackjack_betamount)
        moneyText = view.findViewById(R.id.blackjack_money)

        // Disable hit/stand buttons until game starts
        playButton.isEnabled = true
        hitButton.isEnabled = false
        standButton.isEnabled = false

        // Referencias a los TextViews para mostrar los puntajes
        playerScoreText = view.findViewById(R.id.blackjack_playerscore)
        dealerScoreText = view.findViewById(R.id.blackjack_dealerscore)

        // Initialize card views
        casinoCards = listOf(
            view.findViewById(R.id.blackjack_casino_card1),
            view.findViewById(R.id.blackjack_casino_card2),
            view.findViewById(R.id.blackjack_casino_card3),
            view.findViewById(R.id.blackjack_casino_card4),
            view.findViewById(R.id.blackjack_casino_card5)
        )
        playerCardsViews = listOf(
            view.findViewById(R.id.blackjack_player_card1),
            view.findViewById(R.id.blackjack_player_card2),
            view.findViewById(R.id.blackjack_player_card3),
            view.findViewById(R.id.blackjack_player_card4),
            view.findViewById(R.id.blackjack_player_card5)
        )

        // Initialize deck and money
        deck = generateDeck()
        loadMoney()

        // Card values mapping
        cardValues = mapOf(
            "card_clubs_2" to 2, "card_clubs_3" to 3, "card_clubs_4" to 4, "card_clubs_5" to 5,
            "card_clubs_6" to 6, "card_clubs_7" to 7, "card_clubs_8" to 8, "card_clubs_9" to 9,
            "card_clubs_10" to 10, "card_clubs_j" to 10, "card_clubs_q" to 10, "card_clubs_k" to 10, "card_clubs_a" to 11,
            "card_diamonds_2" to 2, "card_diamonds_3" to 3, "card_diamonds_4" to 4, "card_diamonds_5" to 5,
            "card_diamonds_6" to 6, "card_diamonds_7" to 7, "card_diamonds_8" to 8, "card_diamonds_9" to 9,
            "card_diamonds_10" to 10, "card_diamonds_j" to 10, "card_diamonds_q" to 10, "card_diamonds_k" to 10, "card_diamonds_a" to 11,
            "card_hearts_2" to 2, "card_hearts_3" to 3, "card_hearts_4" to 4, "card_hearts_5" to 5,
            "card_hearts_6" to 6, "card_hearts_7" to 7, "card_hearts_8" to 8, "card_hearts_9" to 9,
            "card_hearts_10" to 10, "card_hearts_j" to 10, "card_hearts_q" to 10, "card_hearts_k" to 10, "card_hearts_a" to 11,
            "card_spades_2" to 2, "card_spades_3" to 3, "card_spades_4" to 4, "card_spades_5" to 5,
            "card_spades_6" to 6, "card_spades_7" to 7, "card_spades_8" to 8, "card_spades_9" to 9,
            "card_spades_10" to 10, "card_spades_j" to 10, "card_spades_q" to 10, "card_spades_k" to 10, "card_spades_a" to 11
        )

        playerCards = mutableListOf()
        dealerCards = mutableListOf()

        // Load card back image
        cardBack = ContextCompat.getDrawable(requireContext(), R.drawable.card_back)!!

        // Play button functionality
        playButton.setOnClickListener {
            val bet = betAmountEditText.text.toString().toIntOrNull() ?: 0
            if (!validateBetAmount()) {
                return@setOnClickListener
            }

            if (!gameInProgress) {
                betAmountEditText.isEnabled = false
                currentBet = bet
                substractMoney(currentBet)
                playDealEffect()
                dealCards()
            }
        }

        hitButton.setOnClickListener { hit() }
        standButton.setOnClickListener { stand() }

        betAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateBetAmount()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Return Inflate view
        return view
    }

    // Deals initial cards to player and dealer
    private fun dealCards() {
        endGame(resetVisibility = true)
        gameInProgress = true

        // Clear previous hands and generate new shuffled deck
        playerCards.clear()
        dealerCards.clear()
        deck = generateDeck()

        // Deal sequence (dealer's first card is hidden)
        val dealSequence = listOf(
            Triple(dealerCards, casinoCards[0], true),
            Triple(playerCards, playerCardsViews[0], false),
            Triple(dealerCards, casinoCards[1], false),
            Triple(playerCards, playerCardsViews[1], false)
        )

        // Sequentially animate card dealing with delays
        dealSequence.forEachIndexed { index, (cards, view, hidden) ->
            view.postDelayed({
                cards.add(drawCard()) // Draw card from deck
                view.setImageDrawable(cardBack) // Set card back image initially
                view.visibility = View.VISIBLE
                displayCard(cards.last(), view, hidden) // Display drawn card
            }, index * dealDelay)
        }

        // Update game state after all animations complete
        view?.postDelayed({
            playButton.isEnabled = false
            hitButton.isEnabled = true
            standButton.isEnabled = true
            updateScores()
        }, dealDelay * dealSequence.size.toLong())
    }


    // Function to draw a random card from the deck
    private fun drawCard(): String {
        if (deck.isEmpty()) {
            deck.addAll(generateDeck()) // Regenerate deck if empty
        }
        val card = deck.random()
        deck.remove(card)               // Remove drawn card from deck
        return card
    }


    // Function to display a card with a flipping animation
    private fun displayCard(card: String, imageView: ImageView, isHidden: Boolean) {
        imageView.visibility = View.VISIBLE

        // Show card back if hidden
        if (isHidden) {
            imageView.setImageDrawable(cardBack)
            imageView.rotationY = 0f
            return
        }

        // Create flip animation
        val flipAnimator = ObjectAnimator.ofFloat(imageView, "rotationY", 0f, 180f).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()

            addListener(object : AnimatorListenerAdapter() {
                @SuppressLint("DiscouragedApi")
                override fun onAnimationEnd(animation: Animator) {
                    val resId = resources.getIdentifier(card, "drawable", context?.packageName)
                    imageView.setImageResource(resId)  // Set actual card image
                    imageView.rotationY = 0f
                }
            })
        }

        // Scale animation to add smooth effect
        val scaleX = ObjectAnimator.ofFloat(imageView, "scaleX", 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(imageView, "scaleY", 0.8f, 1f)

        AnimatorSet().apply {
            playTogether(flipAnimator, scaleX, scaleY)
            start()
        }
    }


    // Function to calculate the score of a hand
    private fun calculateScore(cards: List<String>): Int {
        var score = 0
        var aceCount = 0

        for (card in cards) {
            score += cardValues[card] ?: 0                      // Add card value to score
            if (cards.last().endsWith("a")) aceCount++    // Count aces
        }

        // Adjust Aces' value from 11 to 1 when necessary
        while (score > 21 && aceCount > 0) {
            score -= 10
            aceCount--
        }

        return score
    }

    // Function to update the displayed scores for the player and dealer
    private fun updateScores() {
        val playerScore = calculateScore(playerCards)
        val visibleDealerScore = if (gameInProgress) { // If game in progress the value of the first card of dealer is hidden
            cardValues[dealerCards[1]] ?: 0
        } else {
            calculateScore(dealerCards)
        }

        playerScoreText.text = String.format(Locale.getDefault(),"PLAYER's SCORE: %d", playerScore)
        dealerScoreText.text = if (gameInProgress) {
            "DEALER's SCORE: $visibleDealerScore + ?"
        } else {
            "DEALER's SCORE: ${calculateScore(dealerCards)}"
        }
    }

    // Function to generate a new deck of cards
    private fun generateDeck(): MutableList<String> {
        val suits = arrayOf("clubs", "diamonds", "hearts", "spades")
        val values = arrayOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k", "a")
        val deck = mutableListOf<String>()

        for (suit in suits) {
            for (value in values) {
                deck.add("card_${suit}_$value") // Formato for resources
            }
        }

        deck.shuffle()
        return deck
    }


    private fun hit() {
        if (gameInProgress) {
            playHitEffect() // Play hit sound effect

            // Determine the UI position to place the new card (cyclic)
            val cardPosition = playerCards.size % playerCardsViews.size
            val targetView = playerCardsViews[cardPosition]

            // Draw a card and display it
            playerCards.add(drawCard())
            targetView.visibility = View.VISIBLE
            displayCard(playerCards.last(), targetView, isHidden = false)

            // Check if the player has exceeded 21 points (bust)
            val playerScore = calculateScore(playerCards)
            if (playerScore > 21) {
                // Reveal dealer's hidden card
                displayCard(dealerCards[0], casinoCards[0], isHidden = false)
                updateScores()

                // Display losing message
                playerScoreText.text = String.format(Locale.getDefault(),"PLAYER's SCORE: %d (¡YOU LOSE!)", playerScore)
                dealerScoreText.text = String.format(Locale.getDefault(),"DEALER's SCORE: %d", calculateScore(dealerCards))
                endGame(resetVisibility = false)
            } else {
                updateScores()
            }
        }
    }

    private fun stand() {
        // Reveal dealer's hidden card
        displayCard(dealerCards[0], casinoCards[0], isHidden = false)
        updateScores()

        val playerScore = calculateScore(playerCards)
        val dealerScore = calculateScore(dealerCards)

        // Dealer plays only if the player's score is higher and the dealer has a lower score
        if (playerScore in (dealerScore + 1)..21) {
            while (calculateScore(dealerCards) < 17) {
                val cardPosition = dealerCards.size % casinoCards.size
                val targetView = casinoCards[cardPosition]

                // Dealer draws a new card
                dealerCards.add(drawCard())
                targetView.visibility = View.VISIBLE
                displayCard(dealerCards.last(), targetView, isHidden = false)
            }
        }

        // Determine the final result
        val finalPlayerScore = calculateScore(playerCards)
        val finalDealerScore = calculateScore(dealerCards)

        winnings = 0
        val result = when {
            finalPlayerScore > 21 -> "¡YOU LOSE!" // Player busts
            (finalDealerScore > 21 || finalPlayerScore > finalDealerScore) -> {
                playWinEffect()  // Play win sound
                winnings = currentBet*2
                addMoney(winnings)
                "¡YOU WON!"
            }
            finalPlayerScore == finalDealerScore -> "¡DRAW!"
            else -> "¡YOU LOSE!"
        }

        // Saves bet result in the database
        val bet = Bet(
            userId = userId,
            initialBet = currentBet,
            betResult = winnings,
            date = Date(),
            game = "Blackjack"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            repository.addBet(bet)
        }

        // Updates the UI with the final scores and result
        playerScoreText.text = String.format(Locale.getDefault(), "PLAYER's SCORE: %d (%s)", finalPlayerScore, result)
        dealerScoreText.text = String.format(Locale.getDefault(), "DEALER's SCORE: %d", finalDealerScore)

        // Ends the game
        endGame(resetVisibility = false)
    }


    private fun endGame(resetVisibility: Boolean = true) {
        // Reset state buttons
        gameInProgress = false
        playButton.isEnabled = true
        hitButton.isEnabled = false
        standButton.isEnabled = false

        if (resetVisibility) {
            // Resets all dealer's cards to their initial state
            casinoCards.forEachIndexed { index, iv ->
                iv.setImageDrawable(cardBack)
                iv.visibility = if (index < 2) View.VISIBLE else View.GONE
                iv.rotationY = 0f // Reset rotation
                iv.scaleX = 1f // Reset scale
                iv.scaleY = 1f
            }

            // Resets all player's cards to their initial state
            playerCardsViews.forEachIndexed { index, iv ->
                iv.setImageDrawable(cardBack)
                iv.visibility = if (index < 2) View.VISIBLE else View.GONE
                iv.rotationY = 0f
                iv.scaleX = 1f
                iv.scaleY = 1f
            }
            // Resets the scores displayed on screen
            "PLAYER's SCORE: ?".also { playerScoreText.text = it }
            "DEALER's SCORE: ?".also { dealerScoreText.text = it }
        } else {
            betAmountEditText.isEnabled = true
        }
    }

    private fun validateBetAmount() : Boolean {
        val betText = betAmountEditText.text.toString() // Retrieves bet input as a string
        val betValue = betText.toIntOrNull()            // Attempts to convert the bet input into an integer

        // Check if the bet is invalid (non-numeric or less than or equal to zero)
        if (betValue == null || betValue <= 0) {
            betAmountEditText.error = "Invalid bet"
            return false
        } else if (betValue > currentMoney) {
            betAmountEditText.error = "Not enough money"
            return false
        } else {
            betAmountEditText.error = null
            return true
        }
    }

    // Function to play the sound effect
    private fun playDealEffect() {
        // Initialize MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.blackjack_dealstart)

        // Start the sound
        mediaPlayer?.start()

        // Release MediaPlayer when done
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }

    // Function to play the sound effect
    private fun playHitEffect() {
        // Initialize MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.blackjack_hit)

        // Start the sound
        mediaPlayer?.start()

        // Release MediaPlayer when done
        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }

    // Function to play the sound effect
    private fun playWinEffect() {
        // Initialize MediaPlayer with the sound effect
        mediaPlayer = MediaPlayer.create(context, R.raw.blackjack_win)

        // Start the sound
        mediaPlayer?.start()

        // Release MediaPlayer when done
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
}
