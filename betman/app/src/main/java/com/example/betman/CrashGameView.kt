package com.example.betman

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.View
import kotlin.math.pow
import kotlin.random.Random

// Custom View for the Crash game which handles the games visualisation
class CrashGameView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // Interface to notify when the crash happens and the multiplier value
    interface OnCrashListener {
        fun onCrash(multiplier: Float)
    }

    // Paint object to draw the line representing the crash multiplier (Red color)
    private val paintLine = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        isAntiAlias = true
    }

    // Paint object to display the multiplier value (White text color)
    private val paintText = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Paint object for crash text (Yellow color, larger font size)
    private val paintCrashText = Paint().apply {
        color = Color.YELLOW
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Game state variables
    private var multiplier = 1.0f
    private var crashPoint = 1.0f
    private var isRunning = false
    private var startTime = 0L
    private var mediaPlayer: MediaPlayer? = null
    private var crashListener: OnCrashListener? = null

    // Parameters to control the growth curve of the multiplier
    private val baseGrowth = 0.015f
    private val exponent = 2.3f

    // Method to set the listener for crash events
    fun setOnCrashListener(listener: OnCrashListener) {
        crashListener = listener
    }

    // Method to start the game. Resets game state and begins the multiplier rise
    fun startGame() {
        if (!isRunning) {
            crashPoint = generateCrashPoint()
            multiplier = 1.0f
            startTime = System.currentTimeMillis()
            isRunning = true
            startSoundRise()
            invalidate()
        }
    }

    // Override of onDraw to handle custom drawing of the game
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isRunning) {
            // Update multiplier and redraw canvas while game is running
            updateMultiplier()                          // Update multiplier value
            drawStakeStyleGraph(canvas)                 // Draw multiplier line graph
            postInvalidateDelayed(16)    // Post another invalidation to redraw (16ms for ~60fps)
        } else {
            // If the game has stopped, display the final crash result on top of the graph
            drawStakeStyleGraph(canvas)                 // Draw multiplier line
            drawCrashResult(canvas)                     // Display the crash result (final multiplier)
        }
    }

    // Draw the graph representing the multiplier value
    private fun drawStakeStyleGraph(canvas: Canvas) {
        val width = width.toFloat() // Get the width of the view
        val height = height.toFloat() // Get the height of the view
        // Calculate the current Y position of the multiplier line based on the multiplier value
        val currentY = height - (height * 0.95f * (multiplier - 1.0f) / (crashPoint - 1.0f))

        // Draw the main diagonal line from bottom-left to the current Y position
        canvas.drawLine(0f, height, width, currentY, paintLine)

        // Display the current multiplier value at the right side of the screen
        canvas.drawText(
            "%.2fx".format(multiplier),
            width * 0.9f, // Place the text near the right edge
            currentY - 5f, // Position it just above the current line
            paintText
        )
    }

    // Draw the crash result (final multiplier) when the game ends
    private fun drawCrashResult(canvas: Canvas) {
        // Display the final multiplier at the center of the screen in large yellow text
        canvas.drawText(
            "CRASHED AT %.2fx".format(crashPoint),
            width / 2f,
            height / 2f,
            paintCrashText
        )
    }

    // Update the multiplier based on the time elapsed since the game started
    private fun updateMultiplier() {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        // Apply the growth formula with exponential scaling
        multiplier = 1.0f + baseGrowth * elapsed.pow(exponent)

        // If the multiplier reaches or exceeds the crash point, stop the game
        if (multiplier >= crashPoint) {
            multiplier = crashPoint             // Set multiplier to crash point
            isRunning = false                   // Stop the game
            crashListener?.onCrash(crashPoint)  // Notify listener of crash event
            stopSoundRise()                     // Stop the rising sound effect
            playCrashEffect()                   // Play the crash sound effect
        }
    }

    // Generate a random crash point within predefined ranges based on probabilities
    private fun generateCrashPoint(): Float {
        val rand = Random.nextDouble() // Generate a random value between 0 and 1
        return when {
            rand < 0.6 -> 1.0f + (Random.nextFloat() * 0.5f)       // 60% chance for 1.0x-1.5x
            rand < 0.95 -> 1.5f + (Random.nextFloat() * 1.5f)      // 35% chance for 1.5x-3.0x
            else -> 3.0f + (Random.nextFloat().pow(3) * 50f)    // 5% chance for 3.0x+
        }.coerceAtLeast(1.01f) // Ensure the crash point is at least 1.01x
    }

    // Start the sound effect for the rising multiplier
    private fun startSoundRise() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, R.raw.crash_risingup).apply {
            isLooping = true
            start()
        }
    }

    // Stop the rising sound effect
    private fun stopSoundRise() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Play the crash sound effect when the game ends
    private fun playCrashEffect() {
        MediaPlayer.create(context, R.raw.crash_crash).apply {
            start()
            setOnCompletionListener { release() }
        }
    }

    // Clean up resources when the view is detached from the window
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mediaPlayer?.release()
    }
}