package com.supergeorg.impfterminesachsen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Context.NOTIFICATION_SERVICE
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
import androidx.work.*
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.*
import com.supergeorg.impfterminesachsen.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MyWorker(val ctx : Context, params : WorkerParameters) : Worker(ctx, params)
{
    override fun doWork(): Result
    {
        lateinit var builder: Notification

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val name = "channelname"
        val descriptionText = "channel desc"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("mychannel", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val binding = ActivityMainBinding.inflate(ctx.getSystemService()!!)

        // Instantiate the cache
        //val cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap
        val cache = NoCache()

        // Set up the network to use HttpURLConnection as the HTTP client.
        val network = BasicNetwork(HurlStack())

        // Instantiate the RequestQueue with the cache and network. Start the queue.
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }

        val sharedPref = ctx.getSharedPreferences("impfe", MODE_PRIVATE)
        val savedIZName = sharedPref.getString("impfzentrum", "Dresden IZ")
        val savedMin = sharedPref.getInt("minanz", 0)

        val url = "https://countee-impfee.b-cdn.net/api/1.1/de/counters/getAll/_iz_sachsen?cached=impfee"

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                var resultString = ""
                val data = response.getJSONObject("response").getJSONObject("data")
                val datakeys = data.keys()
                for (key in datakeys) {
                    val impfcenter = data.getJSONObject(key)
                    val name = impfcenter.getString("name")
                    val anzahl = impfcenter.getJSONArray("counteritems").getJSONObject(0).getInt("val")
                    resultString += name
                    resultString += " - "
                    resultString += anzahl
                    resultString += "\n"

                    if ((impfcenter.getString("name") == savedIZName) and (anzahl > savedMin)) {
                        builder = Notification.Builder(ctx, "mychannel")
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Impfe")
                            .setContentText("Es gibt Impfe")
                            .build()

                        notificationManager.notify(1234, builder)
                    }
                }
                binding.impftermineView.text = resultString //"Response: %s".format(response.toString())
            },
            { error ->
                binding.impftermineView.text = error.localizedMessage
                print(error)
                // TODO: Handle error
            }
        )
        // Access the RequestQueue through your singleton class.
        requestQueue.add(jsonObjectRequest)
        return Result.success()
    }
}

class MainActivity : AppCompatActivity() {
    //lateinit var notificationManager: NotificationManager
    lateinit var notificationChannel: NotificationChannel
    lateinit var builder: Notification

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)

        val workManager = WorkManager.getInstance(applicationContext)
        workManager.enqueueUniquePeriodicWork(
            "workName",
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequest
                .Builder(MyWorker::class.java, 30L, TimeUnit.MINUTES)
                .build())

        // Instantiate the cache
        //val cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap
        val cache = NoCache()

        // Set up the network to use HttpURLConnection as the HTTP client.
        val network = BasicNetwork(HurlStack())

        // Instantiate the RequestQueue with the cache and network. Start the queue.
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }

            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
        val name = "channelname"
        val descriptionText = "channel desc"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("mychannel", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        binding.numberPicker.minValue = 0
        binding.numberPicker.maxValue = 1000
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            this,
            R.array.impfzentren_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.centerSpinner.adapter = adapter
        }

        val sharedPref = this.getSharedPreferences("impfe", MODE_PRIVATE)
        val savedIZNo = sharedPref.getInt("impfzentrumNO", 0)
        binding.centerSpinner.setSelection(savedIZNo)
        val savedMin = sharedPref.getInt("minanz", 0)
        binding.numberPicker.value = savedMin

        binding.buttonSave.setOnClickListener {
            //val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
            with (sharedPref.edit()) {
                putString("impfzentrum", binding.centerSpinner.selectedItem.toString())
                putInt("impfzentrumNO", binding.centerSpinner.selectedItemId.toInt())
                putInt("minanz", binding.numberPicker.value)
                apply()
            }

            //val name = sharedPreferences.getString("signature", "")

        }

        binding.button.setOnClickListener {
            val url = "https://countee-impfee.b-cdn.net/api/1.1/de/counters/getAll/_iz_sachsen?cached=impfee"

            val jsonObjectRequest = JsonObjectRequest(
                Request.Method.GET, url, null,
                { response ->
                    val minAnzahl = binding.numberPicker.value
                    val selectedCenter = binding.centerSpinner.selectedItem.toString()
                    var resultString = ""
                    val data = response.getJSONObject("response").getJSONObject("data")
                    val datakeys = data.keys()
                    for (key in datakeys) {
                        val impfcenter = data.getJSONObject(key)
                        val name = impfcenter.getString("name")
                        val counter = impfcenter.getJSONArray("counteritems").getJSONObject(0)
                        val anzahl = counter.getInt("val")
                        resultString += name
                        resultString += " - "
                        resultString += anzahl
                        resultString += "\n"

                        //if ((impfcenter.getInt("id") == 543) and (anzahl > minAnzahl)) {
                        if ((impfcenter.getString("name") == selectedCenter) and (anzahl > minAnzahl)) {
                            builder = Notification.Builder(this, "mychannel")
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setContentTitle("Impfe")
                                .setContentText("Es gibt Impfe")
                                .build()

                            notificationManager.notify(1234, builder)
                        }
                    }
                    binding.impftermineView.text = resultString //"Response: %s".format(response.toString())
                },
                { error ->
                    binding.impftermineView.text = error.localizedMessage
                    print(error)
                // TODO: Handle error
                }
            )
            // Access the RequestQueue through your singleton class.
                    //val requestQueue = Volley.newRequestQueue(applicationContext)
            requestQueue.add(jsonObjectRequest)
        }

        setContentView(binding.root)
    }

}