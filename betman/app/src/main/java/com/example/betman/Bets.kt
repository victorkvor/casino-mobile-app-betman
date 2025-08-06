package com.example.betman

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Bets : Fragment() {
    // UI components
    private lateinit var moneyText: TextView
    private lateinit var betsAdapter: BetsAdapter
    private lateinit var betsRecyclerView: RecyclerView
    private lateinit var betsRecyclerView2: RecyclerView
    private lateinit var repository: CasinoRepository
    private var userId: Long = -1

    companion object {
        // Method to create the fragment and pass the user ID
        fun newInstance(userId: Long): Bets {
            return Bets().apply {
                arguments = Bundle().apply {
                    putLong("USER_ID", userId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve user ID from arguments
        userId = arguments?.getLong("USER_ID") ?: -1
        repository = CasinoRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout
        val view = inflater.inflate(R.layout.fragment_bets, container, false)

        // Display user money (balance)
        moneyText = view.findViewById(R.id.bets_money)
        loadMoney()

        // -------------------- Local Bets RecyclerView -------------------- //
        betsRecyclerView = view.findViewById(R.id.bets_casinorv)
        betsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Set up adapter with empty list initially
        betsAdapter = BetsAdapter(
            emptyList(),
            repository,
            viewLifecycleOwner.lifecycleScope
        )
        betsRecyclerView.adapter = betsAdapter

        // Observe local bets from database (LiveData)
        repository.latestBets().observe(viewLifecycleOwner) { bets ->
            bets?.let { betsAdapter.updateBets(it) }
        }

        // -------------------- API Bets RecyclerView --------------------
        betsRecyclerView2 = view.findViewById(R.id.bets_othercasinorv)
        betsRecyclerView2.layoutManager = LinearLayoutManager(requireContext())

        fetchBetsFromAPI()

        return view
    }

    private fun fetchBetsFromAPI() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Trust all certificates to be able to retrieve the data, as it's flagged as insecure
                trustAllCertificates()

                // URL of API
                val url = URL("https://my.api.mockaroo.com/bets.json?key=c020cd90")
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"

                // If request is successful, parse and load the data
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val betsList = parseBetsFromJson(responseText)

                    // Switch to Main thread to update UI
                    withContext(Dispatchers.Main) {
                        val apiAdapter = BetsAdapterAPI(betsList)
                        betsRecyclerView2.adapter = apiAdapter
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Function that disables SSL verification for testing
    private fun trustAllCertificates() {
        try {
            // Create a trust manager that does not perform any verification
            val trustAllCertificates = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate>? = null

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            })

            // Set up the SSL context to use the trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCertificates, java.security.SecureRandom())

            // Set the default SSLSocketFactory to the one that trusts all certificates
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)

            // Set the hostname verifier to accept all hostnames
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: KeyManagementException) {
            e.printStackTrace()
        }
    }

    // Parse JSON to List<BetAPI>
    private fun parseBetsFromJson(jsonResponse: String): List<BetAPI> {
        val betsList = mutableListOf<BetAPI>()
        val jsonArray = JSONArray(jsonResponse)

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val bet = BetAPI(
                userId = jsonObject.getInt("user_id"),
                username = jsonObject.getString("username"),
                initialBet = jsonObject.getInt("initial_bet"),
                betResult = jsonObject.getInt("bet_result"),
                date = jsonObject.getString("date"),
                game = jsonObject.getString("game")
            )
            betsList.add(bet)
        }
        return betsList
    }

    // Load user Money
    private fun loadMoney() {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentMoney = repository.getMoney(userId)
            withContext(Dispatchers.Main) { moneyText.text = String.format(Locale.getDefault(), "%,d", currentMoney).replace(',', ' ') } // Format number with commas
        }
    }
}
