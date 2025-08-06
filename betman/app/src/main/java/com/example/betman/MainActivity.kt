package com.example.betman

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    // UI components
    private lateinit var userEditText: EditText
    private lateinit var passEditText: EditText
    private lateinit var playButton: AppCompatImageButton
    private lateinit var repository: CasinoRepository
    private var mediaPlayer: MediaPlayer? = null

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    private val notificationPhrases = listOf(
        "🎰 Your luck awaits at Gotham Casino!",
        "🃏 Time to test your fate, joker!",
        "💸 Feeling lucky? Big wins are waiting!",
        "🎲 Step into the shadows and play!",
        "🤑 Come back and hit the jackpot!",
        "👑 Rule the table, King of Gotham!",
        "🎰 Spin the reels of destiny!",
        "🔥 The casino calls... Will you answer?",
        "💀 Risk it all. Gotham style.",
        "🦇 Even Batman can’t stop your winning streak!",
        "⚔️ Dare to defy the odds?",
        "🚬 Light up the night with your bets!",
        "🔮 What will the cards reveal today?",
        "🎯 Aim for the top, legend!",
        "🎉 Your empire of luck begins now!"
    )

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set the proper window insets for gesture navigation
        window.decorView.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                insets.systemGestureInsets.left,
                insets.systemGestureInsets.top,
                insets.systemGestureInsets.right,
                insets.systemGestureInsets.bottom
            )
            insets
        }

        // Set window flags to disable status bar for fullscreen effect
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Check if notification permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permission for notifications
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }

        // Initialize UI components
        userEditText = findViewById(R.id.main_user)
        passEditText = findViewById(R.id.main_pass)
        playButton = findViewById(R.id.main_playbtn)
        repository = CasinoRepository(applicationContext)

        // Start background music and schedule notifications
        startMusic()
        scheduleCasinoReminder() // Schedule hourly reminder for casino notifications
        createNotificationChannel() // Create notification channel for reminders

        // Set click listener for play button
        playButton.setOnClickListener {
            val username = userEditText.text.toString().trim()
            val password = passEditText.text.toString().trim()

            // Simple input validation
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and password required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Perform database operations on background thread using coroutine
            lifecycleScope.launch(Dispatchers.IO) {
                val existingUser = repository.getUserByUsername(username)

                // Check if user exists
                if (existingUser != null) {
                    // Validate password
                    if (existingUser.password == password) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Login successful",
                                Toast.LENGTH_SHORT
                            ).show()
                            stopMusic() // Stop background music on login
                            navigateToHostActivity(existingUser.id_user) // Navigate to next screen
                        }
                    } else {
                        // Wrong password
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Incorrect password",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // User does not exist, create a new one
                    val countryCode = getCountryCode(this@MainActivity)

                    val newUser = User(
                        username = username,
                        password = password,
                        money = 1000, // Default money for new user
                        profileImage = null, // No profile image yet
                        countryCode = countryCode // Retrieved from device info
                    )

                    // Insert new user into the database
                    val newUserId = repository.addUser(newUser)

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "User created successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        stopMusic() // Stop background music
                        navigateToHostActivity(newUserId) // Navigate to the host activity
                    }
                }
            }
        }
    }

    // Navigate to HostActivity, passing the user ID
    private fun navigateToHostActivity(userId: Long) {
        val intent = Intent(this, HostActivity::class.java).apply {
            putExtra("USER_ID", userId)
        }
        startActivity(intent)
        finish() // Close the current activity
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_up, R.anim.stay) // Transition effect
    }

    // Start background music and loop it
    private fun startMusic() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.main_music).apply {
                isLooping = true
                start()
            }
        }
    }

    // Stop background music and release MediaPlayer resources
    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Set the volume level of the music (from 0.0 to 1.0)
    private fun setMusicVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    // Retrieve user's country code from SIM or device locale
    private fun getCountryCode(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simCountry = telephonyManager.simCountryIso
        if (!simCountry.isNullOrEmpty()) return simCountry.uppercase(Locale.getDefault())

        return Locale.getDefault().country.uppercase(Locale.getDefault()) // Fallback to locale
    }

    // Set music volume to zero when app is paused (in background)
    override fun onPause() {
        super.onPause()
        setMusicVolume(0f) // Mute music when app goes to background
    }

    // Restore music volume when app is resumed (comes back to foreground)
    override fun onResume() {
        super.onResume()
        setMusicVolume(1f) // Restore full music volume when app is back
    }

    // Handle the back button to exit the app completely
    @Deprecated("This method has been deprecated")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
        finishAffinity() // Close all activities and exit
    }

    // Function to create a notification channel for reminders
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Casino Reminder"
            val descriptionText = "Reminds user to play at Gotham Casino"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("casino_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Function to schedule notifications every hour
    private fun scheduleCasinoReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("PHRASES", notificationPhrases.toTypedArray()) // Pass phrases for notification
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Set alarm to trigger in 1 hour
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.HOUR, 1) // Set trigger time
        }

        // Set the alarm to trigger even when the device is idle
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }


    // Handle the permission result for notifications
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed creating notification channel
                createNotificationChannel()
            } else {
                // Permission denied, inform the user
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}