package com.javier.monitorhumo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Servicio de Firebase que recibe mensajes push en segundo plano.
 * Al recibir una alerta de humo: muestra notificación, vibra y
 * lanza AlertaActivity automáticamente aunque el celular esté bloqueado.
 */
public class MiFirebaseService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String titulo = "⚠️ HUMO DETECTADO";
        String mensaje = "El sensor MQ-2 detectó humo!";
        String valor = "";

        // Leer datos del mensaje (tipo data para garantizar onMessageReceived)
        if (remoteMessage.getData().size() > 0) {
            if (remoteMessage.getData().containsKey("titulo"))
                titulo = remoteMessage.getData().get("titulo");
            if (remoteMessage.getData().containsKey("cuerpo")) {
                mensaje = remoteMessage.getData().get("cuerpo");
                // Extraer el número del texto "El sensor MQ-2 detectó humo. Valor: 1823"
                if (mensaje.contains("Valor: ")) {
                    valor = mensaje.substring(mensaje.indexOf("Valor: ") + 7);
                }
            }
        }

        vibrar();
        mostrarNotificacion(titulo, mensaje);
        abrirActividad(valor);
    }

    /** Vibración corta al recibir la notificación */
    private void vibrar() {
        long[] patron = {0, 500, 200, 500, 200, 500};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                Vibrator v = vm.getDefaultVibrator();
                v.vibrate(VibrationEffect.createWaveform(patron, -1));
            }
        } else {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(patron, -1);
        }
    }

    /**
     * Lanza AlertaActivity pasando el valor del sensor.
     * Usa PendingIntent para poder abrir desde segundo plano.
     */
    private void abrirActividad(String valor) {
        Intent intent = new Intent(this, AlertaActivity.class);
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP |
            Intent.FLAG_ACTIVITY_NO_USER_ACTION
        );
        intent.putExtra("valor", valor);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    /** Muestra la notificación en la barra de estado con alta prioridad */
    private void mostrarNotificacion(String titulo, String mensaje) {
        String channelId = "humo_channel_v3";

        Intent intent = new Intent(this, AlertaActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

        NotificationManager manager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Crear canal de notificaciones (requerido en Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "Alertas de Humo v3",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alertas de detección de humo");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            channel.setSound(sonido, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            channel.setShowBadge(true);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setAutoCancel(false)
            .setSound(sonido, AudioManager.STREAM_ALARM)
            .setVibrate(new long[]{0, 500, 200, 500})
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true);

        manager.notify(1, builder.build());
    }

    /**
     * Se ejecuta cuando Firebase asigna un nuevo token al dispositivo.
     * Envía el token al servidor AWS para mantenerlo actualizado.
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://18.188.183.205:3000/token");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String body = "{\"token\":\"" + token + "\"}";
                conn.getOutputStream().write(body.getBytes());
                conn.getResponseCode();
                conn.disconnect();
                android.util.Log.d("MonitorHumo", "Token enviado al servidor: " + token);
            } catch (Exception e) {
                android.util.Log.e("MonitorHumo", "Error enviando token: " + e.getMessage());
            }
        }).start();
    }
}
