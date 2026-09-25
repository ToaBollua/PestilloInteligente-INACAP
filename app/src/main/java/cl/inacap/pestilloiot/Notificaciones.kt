package cl.inacap.pestilloiot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object Notificaciones {

    const val CANAL_ID = "alertas_pestillo"
    private var canalCreado = false

    fun inicializarCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canalCreado) {
            val nombre = context.getString(R.string.canal_alertas_nombre)
            val descripcion = context.getString(R.string.canal_alertas_desc)
            val importancia = NotificationManager.IMPORTANCE_HIGH

            val canal = NotificationChannel(CANAL_ID, nombre, importancia).apply {
                this.description = descripcion
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(canal)
            canalCreado = true
        }
    }

    fun mostrarNotificacion(context: Context, titulo: String, mensaje: String, idNotificacion: Int = 1001) {
        inicializarCanal(context)

        // Verificar permiso en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val builder = NotificationCompat.Builder(context, CANAL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(idNotificacion, builder.build())
    }
}
