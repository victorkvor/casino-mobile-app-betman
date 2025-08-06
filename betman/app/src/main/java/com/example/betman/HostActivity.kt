package com.example.betman

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

class HostActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null // MediaPlayer to handle background music playback
    private var userId: Long = -1 // Default value for userId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host) // Set layout to the activity

        // Get the userId from the intent
        userId = intent.getLongExtra("USER_ID", -1) // Retrieve the user ID

        // Start background music for the activity
        startMusic()

        // Setup the toolbar (action bar)
        val toolbar: Toolbar = findViewById(R.id.top_bar)
        setSupportActionBar(toolbar)

        // Enable the back button in the toolbar (to navigate back to the previous screen)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // If there's no saved instance (first time loading the activity), load the Dashboard fragment
        if (savedInstanceState == null) {
            loadFragment(Dashboard.newInstance(userId), "Dashboard")
        }
    }

    // Function to start the background music for the activity
    private fun startMusic() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.casino_music).apply {
                isLooping = true
                setVolume(0.4f, 0.4f) // Set initial volume to 40%
                start()
            }
        }
    }

    // Function to stop and release the background music
    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Function to set the volume of the background music
    private fun setMusicVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    // Handles toolbar menu item selections
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                val dashboardFragment = supportFragmentManager.findFragmentByTag("Dashboard")
                if (dashboardFragment != null && dashboardFragment.isVisible) {
                    logoutAndNavigateToLogin()
                } else {
                    supportFragmentManager.popBackStack()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Handles the system back button press
    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        val dashboardFragment = supportFragmentManager.findFragmentByTag("Dashboard")
        if (dashboardFragment != null && dashboardFragment.isVisible) {
            logoutAndNavigateToLogin()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // Function to load a fragment
    private fun loadFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .addToBackStack(null)
            .commit()
    }

    // Function to log out and navigate back to the login screen
    fun logoutAndNavigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        stopMusic()
        startActivity(intent)
        finish() // Finalizar HostActivity para que el usuario no pueda volver
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_up, R.anim.slide_down)
    }

    // Stop the music when the app is paused (minimized or goes to the background)
    override fun onPause() {
        super.onPause()
        setMusicVolume(0f) // Set volume to 0 when app is paused (minimized)
    }

    // Resume the music when the app is resumed (comes back to the foreground)
    override fun onResume() {
        super.onResume()
        setMusicVolume(0.4f) // Restore volume to normal (40%)
    }
}