package com.javier.monitorhumo;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Pantalla de alerta que emerge automáticamente cuando el sensor
 * detecta humo. Muestra fondo rojo parpadeante, valor del sensor,
 * hora de detección, tiempo transcurrido, vibración continua y
 * sonido de pitido adaptativo según el nivel de humo.
 */
public class AlertaActivity extends AppCompatActivity {

    private Vibrator vibrador;
    private Ringtone ringtone;
    private ValueAnimator animador;
    private Handler handler = new Handler();
    private long tiempoInicio;
    private Runnable contadorRunnable;
    private Runnable pitidoRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Flags para emerger aunque el celular esté bloqueado
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_alerta);

        // Mostrar valor del sensor recibido por el Intent
        String valor = getIntent().getStringExtra("valor");
        TextView tvValor = findViewById(R.id.tvValor);
        tvValor.setText("Valor sensor: " + (valor != null && !valor.isEmpty() ? valor : "—"));

        // Mostrar hora exacta de la alerta
        TextView tvHora = findViewById(R.id.tvHora);
        String horaActual = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvHora.setText("🕐 Hora: " + horaActual);

        // Contador de tiempo transcurrido desde la alerta
        tiempoInicio = System.currentTimeMillis();
        TextView tvTiempo = findViewById(R.id.tvTiempo);
        contadorRunnable = new Runnable() {
            @Override
            public void run() {
                long segundos = (System.currentTimeMillis() - tiempoInicio) / 1000;
                long mins = segundos / 60;
                long segs = segundos % 60;
                if (mins > 0) {
                    tvTiempo.setText("⏱ Transcurrido: " + mins + "m " + segs + "s");
                } else {
                    tvTiempo.setText("⏱ Transcurrido: " + segs + "s");
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(contadorRunnable);

        iniciarVibracion();
        iniciarPitido(valor);
        iniciarAnimacion();

        // Botón para reconocer y detener la alarma
        Button btnReconocer = findViewById(R.id.btnReconocer);
        btnReconocer.setOnClickListener(v -> {
            detenerTodo();
            finish();
        });
    }

    /**
     * Calcula el intervalo del pitido según el valor del sensor.
     * Mayor concentración de humo = pitido más rápido.
     * @param valorStr valor del sensor como String
     * @return intervalo en milisegundos
     */
    private int calcularIntervalo(String valorStr) {
        try {
            int valor = Integer.parseInt(valorStr.trim());
            if (valor >= 2500) return 300;   // Muy peligroso — pitido rápido
            if (valor >= 1500) return 1000;  // Peligroso — pitido medio
            return 2000;                      // Leve — pitido lento
        } catch (Exception e) {
            return 1000;
        }
    }

    /**
     * Inicia el pitido intermitente adaptativo.
     * La velocidad varía según el nivel de humo detectado.
     */
    private void iniciarPitido(String valorStr) {
        try {
            Uri sonido = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone = RingtoneManager.getRingtone(this, sonido);
            int intervalo = calcularIntervalo(valorStr != null ? valorStr : "1000");

            pitidoRunnable = new Runnable() {
                @Override
                public void run() {
                    if (ringtone != null) {
                        if (ringtone.isPlaying()) ringtone.stop();
                        ringtone.play();
                    }
                    handler.postDelayed(this, intervalo);
                }
            };
            handler.post(pitidoRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Inicia vibración continua con patrón repetitivo */
    private void iniciarVibracion() {
        long[] patron = {0, 500, 300}; // espera, vibra, pausa — se repite
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                vibrador = vm.getDefaultVibrator();
                vibrador.vibrate(VibrationEffect.createWaveform(patron, 0));
            }
        } else {
            vibrador = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrador != null) vibrador.vibrate(patron, 0);
        }
    }

    /** Inicia animación de parpadeo rojo en el fondo */
    private void iniciarAnimacion() {
        ConstraintLayout fondo = findViewById(R.id.fondoPrincipal);
        animador = ValueAnimator.ofObject(
            new ArgbEvaluator(),
            Color.parseColor("#CC0000"),
            Color.parseColor("#FF0000")
        );
        animador.setDuration(600);
        animador.setRepeatMode(ValueAnimator.REVERSE);
        animador.setRepeatCount(ValueAnimator.INFINITE);
        animador.addUpdateListener(animator ->
            fondo.setBackgroundColor((int) animator.getAnimatedValue())
        );
        animador.start();
    }

    /** Detiene inmediatamente todos los efectos de la alarma */
    private void detenerTodo() {
        if (pitidoRunnable != null) {
            handler.removeCallbacks(pitidoRunnable);
            pitidoRunnable = null;
        }
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
        if (vibrador != null) {
            vibrador.cancel();
            vibrador = null;
        }
        if (animador != null) {
            animador.cancel();
            animador = null;
        }
        if (contadorRunnable != null) {
            handler.removeCallbacks(contadorRunnable);
            contadorRunnable = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerTodo();
    }
}
