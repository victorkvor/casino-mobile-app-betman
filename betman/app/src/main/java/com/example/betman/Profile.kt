package com.example.betman

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class Profile : Fragment() {
    // UI components
    private lateinit var moneyText: TextView
    private lateinit var usernameText: TextView
    private lateinit var rankingText: TextView
    private lateinit var mgamesplayedText: TextView
    private lateinit var favmgameText: TextView
    private lateinit var earningsText: TextView
    private lateinit var imageProfile: ImageView
    private lateinit var currentLevel: TextView
    private lateinit var nextLevel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var imageflag: ImageView
    private lateinit var logoutButton: ImageButton
    private lateinit var removeaccButton: ImageButton
    private lateinit var takePicButton: ImageButton

    // Game data
    private var currentMoney = 0
    private var totalGames = 0

    // User data and repository
    private var userId: Long = -1
    private lateinit var repository: CasinoRepository

    // Create ActivityResultLauncher for permission request
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(requireContext(), "Se requiere permiso de la cámara", Toast.LENGTH_SHORT).show()
            }
        }

    // Create ActivityResultLauncher for activity result
    private val cameraActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                @Suppress("DEPRECATION") val imageBitmap = result.data?.extras?.get("data") as Bitmap
                imageProfile.setImageBitmap(imageBitmap)
                updateProfileImage(imageBitmap)
            }
        }

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Profile {
            return Profile().apply {
                arguments = Bundle().apply { putLong("USER_ID", userId) } // Save userId into arguments
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve user ID from arguments
        userId = arguments?.getLong("USER_ID", -1) ?: -1

        // Initialize repository with context
        repository = CasinoRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // UI components
        moneyText = view.findViewById(R.id.profile_money)
        usernameText = view.findViewById(R.id.profile_username)
        rankingText = view.findViewById(R.id.profile_rank)
        mgamesplayedText = view.findViewById(R.id.profile_minigamesplayed)
        favmgameText = view.findViewById(R.id.profile_favgame)
        earningsText = view.findViewById(R.id.profile_chipswin)
        imageProfile = view.findViewById(R.id.profile_userimg)
        currentLevel = view.findViewById(R.id.profile_levelactual)
        nextLevel = view.findViewById(R.id.profile_levelnext)
        progressBar = view.findViewById(R.id.profile_progressbar)
        imageflag = view.findViewById(R.id.profile_countryimg)
        logoutButton = view.findViewById(R.id.profile_logout)
        removeaccButton = view.findViewById(R.id.profile_deleteprofileimg)
        takePicButton = view.findViewById(R.id.profile_takepicimg)

        // Set up the click listener for the "take picture" button
        takePicButton.setOnClickListener { checkCameraPermissionAndOpen() }

        // Load user profile and money information from the repository
        loadUserProfile()
        loadMoney()

        return view
    }

    // Function to load the user profile information from the repository
    private fun loadUserProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = repository.getUserById(userId) ?: return@launch
            val betsPlayed = repository.getUserBetCount(userId)
            val favgame = repository.getMostPlayedGame(userId) ?: ""
            val earnings = (repository.getTotalWinnings(userId) ?: 0).toInt()
            val rank = repository.getUserRanking(userId)
            val flagUrl = "https://flagsapi.com/${user.countryCode}/shiny/64.png"

            // Switch to the main thread to update UI components
            withContext(Dispatchers.Main) {
                totalGames = betsPlayed  // Update total games played by the user
                updateLevelProgress()  // Update the level and progress bar

                // Set the user information on the UI
                usernameText.text = user.username
                rankingText.text = String.format(Locale.getDefault(), "%d", rank)
                mgamesplayedText.text = String.format(Locale.getDefault(), "%d", betsPlayed)
                favmgameText.text = favgame
                earningsText.text = if (earnings >= 0) {
                    String.format(Locale.getDefault(), "+%,d", earnings).replace(',', ' ') // Add + for positive earnings
                } else {
                    String.format(Locale.getDefault(), "%,d", earnings).replace(',', ' ') // Format number normally for negative earnings
                }
                earningsText.setTextColor(
                    if (earnings >= 0) android.graphics.Color.GREEN else android.graphics.Color.RED
                )

                // Load the country flag image
                loadImageFromUrl(flagUrl, imageflag)

                // Set the profile image (fallback to a default image if none exists)
                imageProfile.setImageBitmap(user.profileImage ?: BitmapFactory.decodeResource(resources, R.drawable.icon_user))

                // Set up the buttons for logout and account deletion
                val hostActivity = activity as? HostActivity
                logoutButton.setOnClickListener { hostActivity?.logoutAndNavigateToLogin() }
                removeaccButton.setOnClickListener { deleteAccount(hostActivity) }
            }
        }
    }

    // Function to delete the user's account from the repository and navigate to the login screen
    private fun deleteAccount(hostActivity: HostActivity?) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.deleteUserWithBets(userId)
            withContext(Dispatchers.Main) {
                hostActivity?.logoutAndNavigateToLogin()
            }
        }
    }

    // Function to update the user's level and progress based on the total games played
    private fun updateLevelProgress() {
        // Calculate the level based on the square root of the total games played
        val level = (sqrt(totalGames.toDouble()) / 2).toInt() + 1
        val currentLevelGames = ((level - 1) * 2).toDouble().pow(2).toInt()
        val nextLevelGames = (level * 2).toDouble().pow(2).toInt()

        // Calculate the progress toward the next level
        val progress = when {
            totalGames >= nextLevelGames -> 100
            currentLevelGames == nextLevelGames -> 0
            else -> ((totalGames - currentLevelGames).toDouble() / (nextLevelGames - currentLevelGames) * 100).toInt()
        }

        // Update the UI with the calculated level and progress
        currentLevel.text = String.format(Locale.getDefault(), "%d", level)
        nextLevel.text = String.format(Locale.getDefault(), "%d", (level + 1))
        progressBar.progress = progress
    }

    // Function to load money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            currentMoney = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", currentMoney).replace(',', ' ') } // Format number with commas
        }
    }

    // Method to load an image from a URL and set it to an ImageView (flags)
    private fun loadImageFromUrl(urlString: String, imageView: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val inputStream: InputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)

                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(R.drawable.default_flag)
                }
            }
        }
    }

    // Method to check if camera permission is granted and then open the camera
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            openCamera()
        }
    }

    // Method to open the camera and start the image capture intent
    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            cameraActivityResultLauncher.launch(takePictureIntent)
        }
    }

    // Method to update the user's profile image in the repository
    private fun updateProfileImage(imageBitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateProfileImage(userId, imageBitmap)
        }
    }
}