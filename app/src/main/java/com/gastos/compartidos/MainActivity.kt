package com.gastos.compartidos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gastos.compartidos.data.TaskReminderWorker
import com.gastos.compartidos.ui.AppNavigation
import com.gastos.compartidos.ui.theme.GastosTheme
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GastosTheme {
                AppNavigation()
            }
        }

        // Recordatorio diario de tareas de la casa vencidas.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "task-reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TaskReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
