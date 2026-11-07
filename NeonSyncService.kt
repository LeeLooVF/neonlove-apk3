package com.jordan.neonlove

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class NeonSyncService : Service() {

    private var isRunning = true

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(1, buildStatusNotification("Connecté en direct • Prêt à allumer l'écran"))
        startLiveListener()
    }

    private fun startLiveListener() {
        thread(start = true) {
            while (isRunning) {
                try {
                    val prefs = getSharedPreferences("NeonLovePrefs", Context.MODE_PRIVATE)
                    val code = prefs.getString("room_code", "JORDAN-LYDIE") ?: "JORDAN-LYDIE"
                    val url = URL("https://ntfy.sh/neonlove_${code.lowercase()}_alert/json")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 0

                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var line: String?
                    while (isRunning && reader.readLine().also { line = it } != null) {
                        val content = line ?: continue
                        if (content.contains("\"event\":\"message\"")) {
                            wakeLockScreenImmediately()
                        }
                    }
                    reader.close()
                } catch (e: Exception) {
                    Thread.sleep(5000)
                }
            }
        }
    }

    private fun wakeLockScreenImmediately() {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotification = NotificationCompat.Builder(this, "NEON_LOCKSCREEN_ALERT")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✨ Message Néon en direct !")
            .setContentText("Nouveau dessin lumineux affiché sur l'écran")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, alertNotification)

        MainActivity.instance?.wakeUpAndUnlockScreen()
        try {
            startActivity(fullScreenIntent)
        } catch (_: Exception) {}
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                "NEON_SYNC_BG",
                "Veille NeonLove Live",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(serviceChannel)

            val alertChannel = NotificationChannel(
                "NEON_LOCKSCREEN_ALERT",
                "Réveil Écran Verrouillé",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(alertChannel)
        }
    }

    private fun buildStatusNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "NEON_SYNC_BG")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("NeonLove Live (Jordan & Lydie)")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
