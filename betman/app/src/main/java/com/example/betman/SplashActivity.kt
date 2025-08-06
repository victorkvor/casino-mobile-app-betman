package com.example.betman

import android.annotation.SuppressLint
import android.content.Intent
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    // UI components
    private lateinit var logoImage: ImageView
    private lateinit var pageImage: ImageView
    private var currentImageIndex = 0
    private var currentDelay = 1000L  // Delay inicial
    private val minDelay = 100L       // Delay mínimo

    // Array of comic images to show one by one
    private val images = arrayOf(
        R.drawable.comic1,
        R.drawable.comic2,
        R.drawable.comic3,
        R.drawable.comic4,
        R.drawable.comic5,
        R.drawable.comic6,
        R.drawable.comic7,
        R.drawable.comic8,
        R.drawable.comic9,
        R.drawable.comic10
    )

    // Sound system setup
    private lateinit var soundPool: SoundPool
    private var pageTurnSoundId = 0
    private var logoAppearanceSoundId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // UI components
        logoImage = findViewById(R.id.logoImage)
        pageImage = findViewById(R.id.pageImage)
        pageImage.setImageResource(images[currentImageIndex])

        // Initialize SoundPool for fast short audio effects
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)  // Allow multiple sounds to play simultaneously
            .build()

        // Preload audio resources
        pageTurnSoundId = soundPool.load(this, R.raw.page_flip_sound, 1)
        logoAppearanceSoundId = soundPool.load(this, R.raw.logo_appearance_sound, 1)

        // Handler to post delayed image changes (runs on main thread)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                // Update the current image index, loop back after last one
                currentImageIndex = (currentImageIndex + 1) % images.size
                pageImage.setImageResource(images[currentImageIndex])

                // Speed and volume calculation based on animation progress
                val progress = (1000L - currentDelay).toFloat() / 900f // 900 = 1000-100
                val volume = 0.8f - (progress * 0.7f) // 80% → 10%
                val clampedVolume = volume.coerceIn(0.1f, 0.8f)

                // Fixed speed calculation
                val playbackRate = 1f + (progress * 2f) // 1x → 3x

                // Play the comic flip sound with dynamic volume and speed
                soundPool.play(
                    pageTurnSoundId,
                    clampedVolume,
                    clampedVolume,
                    1,
                    0,
                    playbackRate
                )


                // Decrease delay to speed up animation, stop at minDelay (70ms por paso)
                if (currentDelay > minDelay) {
                    currentDelay -= 70L
                }

                // Keep running the animation if there are still images
                if (currentImageIndex < images.size) {
                    handler.postDelayed(this, currentDelay)
                }
            }
        }, 0)

        // After 6 seconds, reveal the logo and transition to main app
        handler.postDelayed({
            logoImage.visibility = View.VISIBLE
            // Apply fade-in animation to logo with bounce (overshoot) effect
            val fadeInLogo = AnimationUtils.loadAnimation(this, R.anim.fade_in).apply {
                interpolator = OvershootInterpolator(2.0f)
                duration = 1200L
            }
            logoImage.startAnimation(fadeInLogo)

            // Play sound effect for logo reveal
            soundPool.play(logoAppearanceSoundId, 1f, 1f, 0, 0, 1f)

            // Delay transition to main activity to let logo animation play
            handler.postDelayed({
                startActivity(Intent(this, MainActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }, 1500)
        }, 6000)
    }

    // Function to properly release SoundPool resources to avoid memory leaks
    override fun onDestroy() {
        soundPool.release()
        super.onDestroy()
    }
}