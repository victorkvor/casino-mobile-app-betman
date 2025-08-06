package com.example.betman

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class RouletteGameView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // Interface to notify when the roulette spin ends
    interface OnSpinEndListener {
        fun onSpinEnd(winningNumber: Int)
    }

    private var spinEndListener: OnSpinEndListener? = null

    // Paint objects for drawing the ball, sectors, numbers, and winning text
    private val paintBall = Paint().apply {
        color = Color.parseColor("#FFD700") // Gold color for the ball
        style = Paint.Style.FILL
    }
    // Each sector will have a different color
    private val paintSector = Paint().apply { style = Paint.Style.FILL }

    private val paintText = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val paintWinning = Paint().apply {
        color = Color.YELLOW
        textSize = 60f
        textAlign = Paint.Align.CENTER
    }

    // Standard roulette numbers in the wheel layout order
    private val numbers = arrayOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
        24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    )

    // Angles and speeds used for the spinning animation
    private var angle = 0f // Angle of the roulette sectors
    private var ballAngle = 0f // Angle of the ball
    private var ballSpeed = 30f
    private var rouletteSpeed = 20f
    private var isSpinning = false // Indicates if roulette is currently spinning
    private var winningNumber = -1 // The final result after spinning
    private var circleBitmap: Bitmap? = null // Optional image in the center
    private val paintImage = Paint().apply { isAntiAlias = true }

    private var mediaPlayer: MediaPlayer? = null // For spin sound effect

    // Register a listener to be notified when the spin ends
    fun setOnSpinEndListener(listener: OnSpinEndListener) {
        spinEndListener = listener
    }

    // Start the roulette spin
    fun startRoulette() {
        if (!isSpinning) {
            isSpinning = true
            angle = 0f
            ballAngle = 0f
            // Random ball speed between 30 and 50
            ballSpeed = Random.nextFloat() * 20 + 30
            rouletteSpeed = ballSpeed / 2
            winningNumber = -1
            invalidate() // Trigger redraw
            playRouletteEffect()
        }
    }

    // Main draw method
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 2.5f

        // Draw all sectors with numbers
        for (i in numbers.indices) {
            val sectorAngle = 360f / numbers.size * i
            paintSector.color = getSectorColor(numbers[i])
            drawSector(canvas, centerX, centerY, radius, sectorAngle + angle, paintSector)
            drawNumber(canvas, centerX, centerY, radius, sectorAngle + angle, numbers[i])
        }

        // Draw center image using a shader
        circleBitmap?.let { bitmap ->
            // Create a BitmapShader to fill a shape with the bitmap image
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            // Calculate a scale factor so the image fits inside the circle
            val scale = max(
                (radius * 2 - 200f) / bitmap.width,
                (radius * 2 - 200f) / bitmap.height
            )

            // Create a transformation matrix to scale and center the bitmap
            Matrix().apply {
                setScale(scale, scale)
                // Center the scaled bitmap in the view
                postTranslate(
                    centerX - (bitmap.width * scale) / 2,
                    centerY - (bitmap.height * scale) / 2
                )

                // Apply the transformation to the shader
                shader.setLocalMatrix(this)
            }
            // Assign the shader to the paint object so it draws the bitmap as a texture
            paintImage.shader = shader

            // Draw the circular image at the center of the roulette wheel
            canvas.drawCircle(centerX, centerY, radius - 100f, paintImage)
        }

        // Draw the roulette ball
        val ballX = centerX + (radius - 40) * cos(Math.toRadians(ballAngle.toDouble())).toFloat()
        val ballY = centerY + (radius - 40) * sin(Math.toRadians(ballAngle.toDouble())).toFloat()
        canvas.drawCircle(ballX, ballY, 15f, paintBall)

        // Update the spinning state and request next frame if still spinning
        if (isSpinning) {
            updateSpinningState()
            postInvalidateDelayed(16) // ~60 FPS
        }

        if (!isSpinning && winningNumber != -1) {
            canvas.drawText("Winner: $winningNumber", centerX, centerY, paintWinning)
        }
    }

    // Updates angle and speed, gradually slowing down spin
    private fun updateSpinningState() {
        angle += rouletteSpeed
        ballAngle -= ballSpeed
        rouletteSpeed *= 0.98f // Simulate friction
        ballSpeed *= 0.97f

        // Once ball is slow enough, stop and calculate result
        if (ballSpeed < 1f) {
            isSpinning = false
            calculateWinningNumber()
        }
    }

    // Determine the winning number based on ball's final angle
    private fun calculateWinningNumber() {
        // Calculate the angle difference between the ball and the wheel
        val relativeAngle = (ballAngle - angle) % 360f

        // Normalize the angle
        val adjustedAngle = (relativeAngle + 360f) % 360f

        // Determine the angular size of one sector
        val sectorWidth = 360f / numbers.size

        // Find the index of the sector where the ball landed
        val sectorIndex = (adjustedAngle / sectorWidth).toInt() % numbers.size

        // Get the corresponding number from the numbers array
        winningNumber = numbers[sectorIndex]

        // Notify listener that the spin has ended and provide the winning number
        spinEndListener?.onSpinEnd(winningNumber)
    }

    // Draws a triangular sector of the roulette
    private fun drawSector(canvas: Canvas, cx: Float, cy: Float, radius: Float, startAngle: Float, paint: Paint) {
        val path = Path().apply {
            // Move to the center of the wheel
            moveTo(cx, cy)

            // Calculate the first outer point of the triangle
            val angleRad = Math.toRadians(startAngle.toDouble())
            val x1 = cx + radius * cos(angleRad).toFloat()
            val y1 = cy + radius * sin(angleRad).toFloat()

            // Calculate the second outer point of the triangle (end of sector)
            val x2 = cx + radius * cos(angleRad + Math.toRadians(360.0 / numbers.size)).toFloat()
            val y2 = cy + radius * sin(angleRad + Math.toRadians(360.0 / numbers.size)).toFloat()

            // Draw the triangular path
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        // Draw the sector using the specified paint
        canvas.drawPath(path, paint)
    }

    // Draw the number text inside its corresponding sector
    private fun drawNumber(canvas: Canvas, cx: Float, cy: Float, radius: Float, startAngle: Float, number: Int) {
        // Calculate the angle at the center of the sector
        val sectorCenterAngle = startAngle + 360f / numbers.size / 2f
        val angleRad = Math.toRadians(sectorCenterAngle.toDouble())

        // Calculate the position to draw the number, slightly inside the radius
        val x = cx + (radius - 60) * cos(angleRad).toFloat()
        val y = cy + (radius - 60) * sin(angleRad).toFloat()


        val text = number.toString()

        // Measure the text bounds (used for alignment)
        val bounds = Rect()
        paintText.getTextBounds(text, 0, text.length, bounds)

        // Save the canvas state before rotating
        canvas.save()

        // Rotate text to align it properly within the sector
        canvas.rotate(sectorCenterAngle + 90, x, y)

        // Draw the number at calculated position, adjusting for bounds
        canvas.drawText(text, x, y - bounds.bottom.toFloat(), paintText)

        // Restore the canvas to avoid affecting subsequent drawing
        canvas.restore()
    }

    // Return the color for a given number on the roulette wheel
    private fun getSectorColor(number: Int): Int = when (number) {
        0 -> Color.parseColor("#70F01F") // Green for 0
        // Red numbers according to standard European roulette
        in setOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36) -> Color.RED
        else -> Color.BLACK
    }

    // Called when the size of the view changes
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Load the bitmap image used for the center of the roulette
        circleBitmap = BitmapFactory.decodeResource(resources, R.drawable.roulette_center)
    }

    // Play the roulette spinning sound effect
    private fun playRouletteEffect() {
        mediaPlayer?.release() // Release any previously playing MediaPlayer
        mediaPlayer = MediaPlayer.create(context, R.raw.roulette_spin).apply {
            start()
            setOnCompletionListener { release() }
        }
    }
}