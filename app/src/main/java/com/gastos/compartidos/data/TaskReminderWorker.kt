package com.gastos.compartidos.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gastos.compartidos.MainActivity
import com.gastos.compartidos.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Revisa una vez al día si hay tareas de la casa vencidas (o que vencen en las
 * próximas 24 h) en los grupos del usuario, y muestra una notificación local.
 */
class TaskReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        return try {
            val db = FirebaseFirestore.getInstance()
            val limit = Timestamp(Date(System.currentTimeMillis() + 24L * 3_600_000))
            val groups = db.collection("groups")
                .whereArrayContains("memberIds", uid)
                .get()
                .await()
            val due = mutableListOf<String>()
            for (groupDoc in groups.documents) {
                val groupName = groupDoc.getString("name") ?: ""
                val tasks = groupDoc.reference.collection("tasks")
                    .whereLessThanOrEqualTo("nextDue", limit)
                    .get()
                    .await()
                for (task in tasks.documents) {
                    val title = task.getString("title") ?: continue
                    due += "$title ($groupName)"
                }
            }
            if (due.isNotEmpty()) showNotification(due)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(dueTasks: List<String>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.task_reminders_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val style = NotificationCompat.InboxStyle()
        dueTasks.take(6).forEach { style.addLine(it) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (dueTasks.size == 1) context.getString(R.string.today_tasks, dueTasks.first())
                else context.resources.getQuantityString(
                    R.plurals.today_tasks_count,
                    dueTasks.size,
                    dueTasks.size,
                )
            )
            .setContentText(dueTasks.joinToString(" · "))
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "tareas_casa"
        const val NOTIFICATION_ID = 1001
    }
}
