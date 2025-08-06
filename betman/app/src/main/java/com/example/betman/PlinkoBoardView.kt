package com.example.betman

import android.content.Context
import android.graphics.*
import android.media.SoundPool
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt
import kotlin.random.Random

class PlinkoBoardView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    // Interface to communicate the result of the Plinko drop
    interface OnPlinkoResultListener {
        fun onPlinkoResult(multiplier: Float) // Sends the multiplier when the ball lands in a slot
    }

    private var resultListener: OnPlinkoResultListener? = null // Listener for result events
    var isBallDropping = false // Flag to indicate if the ball is currently dropping
        private set

    // Paints for rendering different game components
    private val paintPeg = Paint().apply {
        color = Color.parseColor("#70F01F") // Peg color (green)
        style = Paint.Style.FILL
    }

    private val paintBall = Paint().apply {
        color = Color.parseColor("#FFD700") // Ball color (gold)
        style = Paint.Style.FILL
    }

    private val paintSlot = Paint().apply {
        style = Paint.Style.FILL
    }

    private val paintText = Paint().apply {
        color = Color.WHITE // Text color for multipliers and result
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Physics parameters
    private val ballRadius = 20f
    private var ballX = 0f
    private var ballY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private val gravity = 0.8f
    private val bounceDamping = 0.7f
    private val airResistance = 0.99f

    // Game structure data
    private val pegs = mutableListOf<PointF>()
    private val slots = mutableListOf<RectF>()

    // Reward multipliers for each slot
    private val multipliers = listOf(0f, 0.1f, 0.2f, 0.6f, 5f, 20f, 5f, 0.6f, 0.2f, 0.1f, 0f)

    // Reward multipliers for each slot
    private val multiplierColors = listOf(
        Color.parseColor("#FFC000"),
        Color.parseColor("#FF9A0D"),
        Color.parseColor("#FF7319"),
        Color.parseColor("#FF4D26"),
        Color.parseColor("#FF2632"),
        Color.parseColor("#FF003F"),
        Color.parseColor("#FF2632"),
        Color.parseColor("#FF4D26"),
        Color.parseColor("#FF7319"),
        Color.parseColor("#FF9A0D"),
        Color.parseColor("#FFC000")
    )

    // Current game state
    private var currentMultiplier: Float? = null
    private var boardWidth = 0f
    private var boardHeight = 0f
    private var pegSpacing = 0f
    private val wallLeft get() = slots.first().left // Avoid adding too much space
    private val wallRight get() = slots.last().right // Avoid adding too much space

    // Sound system
    private var soundPool: SoundPool = SoundPool.Builder().setMaxStreams(1).build()
    private var bounceSoundId: Int = 0
    private var slotSoundId: Int = 0
    private var bounceCount = 0
    private val basePitch = 1.0f
    private val maxPitch = 2.0f
    private val pitchIncrement = 0.1f

    init {
        // Load sounds from raw resources
        bounceSoundId = soundPool.load(context, R.raw.plinko_ball, 1)
        slotSoundId = soundPool.load(context, R.raw.plinko_slot, 1)
    }

    // Triggered when the view size changes (including initial layout)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boardWidth = w.toFloat()
        boardHeight = h.toFloat()
        pegSpacing = boardWidth / (multipliers.size + 2)
        generateGameElements()
    }

    // Initializes pegs and slots based on board size
    private fun generateGameElements() {
        pegs.clear()
        slots.clear()

        // Generate pegs
        val rows = multipliers.size - 1
        val columnSpacing = boardWidth / (multipliers.size + 2)

        for (i in 1 until rows) {
            val y = (i + 1) * pegSpacing * 1.2f
            for (j in 0 until i + 1) {
                val x = (boardWidth / 2) + (j - i / 2f) * columnSpacing
                pegs.add(PointF(x, y))
            }
        }

        // Generate slots
        val slotWidth = boardWidth / (multipliers.size + 2)
        val startX = (boardWidth - (slotWidth * multipliers.size)) / 2
        val slotY = pegs.lastOrNull()?.y?.plus(40f) ?: boardHeight

        multipliers.forEachIndexed { i, _ ->
            slots.add(RectF(
                startX + (i * slotWidth),
                slotY,
                startX + (i + 1) * slotWidth,
                slotY + 100f
            ))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw pegs
        pegs.forEach { canvas.drawCircle(it.x, it.y, 10f, paintPeg) }

        // Draw slots
        slots.forEachIndexed { i, slot ->
            paintSlot.color = multiplierColors[i]
            canvas.drawRect(slot, paintSlot)
            canvas.drawText("x${multipliers[i]}", slot.centerX(), slot.centerY() + 15f, paintText)
        }

        // Display result if ball has landed
        currentMultiplier?.let {
            canvas.drawText(
                "Fallen in x${"%.1f".format(it)}",
                boardWidth / 2,
                60f,
                paintText
            )
        }

        // Draw ball if it's falling
        if (isBallDropping) {
            canvas.drawCircle(ballX, ballY, ballRadius, paintBall)
        }
    }

    fun dropBall() {
        // Start dropping the ball if it's not already dropping
        if (!isBallDropping) {
            isBallDropping = true
            currentMultiplier = null
            resetBallPosition()
            postDelayed({ updateBall() }, 16L) // Call updateBall every 16ms (approx. 60fps)
        }
    }

    private fun resetBallPosition() {
        // Set ball to initial starting position
        ballX = boardWidth / 2
        ballY = 50f
        velocityX = 0f
        velocityY = 5f
    }

    private fun updateBall() {
        if (!isBallDropping) return

        applyPhysics()       // Update position and speed due to gravity/drag
        checkCollisions()    // Detect peg and wall collisions
        checkSlotEntry()     // Detect if ball has entered a slot

        if (isBallDropping) {
            invalidate()                                // Redraw the view
            postDelayed({ updateBall() }, 16L) // Continue animation
        }
    }

    private fun checkCollisions() {
        // Apply horizontal wall bounce with damping
        if (ballX < wallLeft + ballRadius) {
            ballX = wallLeft + ballRadius
            velocityX *= -bounceDamping
            playBounceSound()  // Play bounce sound with increasing pitch
        } else if (ballX > wallRight - ballRadius) {
            ballX = wallRight - ballRadius
            velocityX *= -bounceDamping
            playBounceSound()  // Play bounce sound with increasing pitch
        }

        // Check for collisions with pegs
        pegs.forEach { peg ->
            val dx = ballX - peg.x
            val dy = ballY - peg.y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < ballRadius + 10f) {
                handlePegCollision(dx, dy, distance)
            }
        }
    }

    private fun applyPhysics() {
        // Apply gravity and air resistance
        velocityY += gravity
        velocityY *= airResistance
        velocityX *= airResistance

        // Keep the ball within the screen horizontally
        if (ballX - ballRadius < 0) ballX = ballRadius
        if (ballX + ballRadius > boardWidth) ballX = boardWidth - ballRadius

        // Update ball position
        ballY += velocityY
        ballX += velocityX
    }

    private fun handlePegCollision(dx: Float, dy: Float, distance: Float) {
        // Calculate normal vector from peg to ball
        val normalX = dx / distance
        val normalY = dy / distance
        val overlap = (ballRadius + 10f) - distance

        // Push ball away from peg
        ballX += normalX * overlap
        ballY += normalY * overlap

        // Reflect velocity using dot product and damping
        val dotProduct = velocityX * normalX + velocityY * normalY
        velocityX = (velocityX - 2 * dotProduct * normalX) * bounceDamping
        velocityY = (velocityY - 2 * dotProduct * normalY) * bounceDamping

        // Add randomness to velocity for more natural movement
        velocityX += Random.nextFloat() * 2f - 1f
        playBounceSound()
    }

    // Function to check if the boll has fallen in a slot
    private fun checkSlotEntry() {
        slots.forEachIndexed { i, slot ->
            if (ballX in slot.left..slot.right &&
                ballY + ballRadius >= slot.top &&
                ballY - ballRadius <= slot.bottom) {

                isBallDropping = false
                ballX = slot.centerX()
                ballY = slot.top + ballRadius
                currentMultiplier = multipliers[i]
                resultListener?.onPlinkoResult(multipliers[i])
                playSlotSound()
                invalidate()
            }
        }
    }
    // Play bounce sound with pitch that increases per sound
    private fun playBounceSound() {
        val pitch = (basePitch + (bounceCount++ * pitchIncrement)).coerceAtMost(maxPitch)
        soundPool.play(bounceSoundId, 1f, 1f, 0, 0, pitch)
    }

    // Play slot sound
    private fun playSlotSound() {
        soundPool.play(slotSoundId, 1f, 1f, 0, 0, 1f)
        bounceCount = 0
    }

    // Lister for the main Plinko class
    fun setOnPlinkoResultListener(listener: OnPlinkoResultListener) {
        resultListener = listener
    }

    // Function to release sound resources when view is destroyed
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundPool.release()
    }
}
