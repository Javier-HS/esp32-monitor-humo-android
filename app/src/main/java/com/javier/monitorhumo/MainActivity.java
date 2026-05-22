package com.javier.monitorhumo;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Actividad principal de la app Monitor de Humo ESP32.
 * Muestra el estado del sensor en tiempo real, gráfica de nivel
 * y controles para LED, monitoreo y sensibilidad del ESP32.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MonitorHumo";
    // URL del servidor AWS EC2
    private static final String SERVER = "http://18.188.183.205:3000";

    // Vistas
    private Button btnLed, btnMonitoreo;
    private TextView tvValorActual, tvEstadoTexto, tvEstadoIcono, tvUmbral;
    private SeekBar seekUmbral;
    private LineChart grafica;
    private LineDataSet dataSet;
    private LineData lineData;

    // Estado actual de los controles
    private boolean ledActivo = false;
    private boolean monitoreoActivo = true;
    private int umbral = 800;

    // Handler para actualización periódica
    private Handler handler = new Handler();
    private Runnable actualizarRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pedirPermisos();
        inicializarVistas();
        configurarGrafica();
        configurarControles();
        cargarConfig();

        // Actualizar datos cada 5 segundos
        actualizarRunnable = new Runnable() {
            @Override
            public void run() {
                cargarDatos();
                cargarConfig();
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(actualizarRunnable);

        // Obtener y renovar token FCM automáticamente
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(task -> {
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task2 -> {
                    if (!task2.isSuccessful()) return;
                    String token = task2.getResult();
                    Log.d(TAG, "Token FCM nuevo: " + token);
                    TextView tvToken = findViewById(R.id.tvToken);
                    tvToken.setText("Token: " + token);
                });
        });
    }

    /** Inicializa las referencias a las vistas del layout */
    private void inicializarVistas() {
        btnLed = findViewById(R.id.btnLed);
        btnMonitoreo = findViewById(R.id.btnMonitoreo);
        tvValorActual = findViewById(R.id.tvValorActual);
        tvEstadoTexto = findViewById(R.id.tvEstadoTexto);
        tvEstadoIcono = findViewById(R.id.tvEstadoIcono);
        tvUmbral = findViewById(R.id.tvUmbral);
        seekUmbral = findViewById(R.id.seekUmbral);
        grafica = findViewById(R.id.grafica);
    }

    /** Configura la gráfica de nivel de humo con estilo oscuro */
    private void configurarGrafica() {
        grafica.setBackgroundColor(Color.parseColor("#161b22"));
        grafica.getDescription().setEnabled(false);
        grafica.getLegend().setTextColor(Color.parseColor("#888888"));
        grafica.getAxisLeft().setTextColor(Color.parseColor("#888888"));
        grafica.getAxisLeft().setAxisMinimum(0f);
        grafica.getAxisLeft().setAxisMaximum(4095f);
        grafica.getAxisLeft().setGridColor(Color.parseColor("#1c2128"));
        grafica.getAxisRight().setEnabled(false);
        grafica.getXAxis().setTextColor(Color.parseColor("#888888"));
        grafica.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        grafica.getXAxis().setGridColor(Color.parseColor("#1c2128"));
        grafica.getXAxis().setLabelCount(5);

        dataSet = new LineDataSet(new ArrayList<>(), "Nivel de humo");
        dataSet.setColor(Color.parseColor("#e94560"));
        dataSet.setFillColor(Color.parseColor("#e94560"));
        dataSet.setDrawFilled(true);
        dataSet.setFillAlpha(30);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setValueTextColor(Color.TRANSPARENT);

        lineData = new LineData(dataSet);
        grafica.setData(lineData);
    }

    /** Configura los listeners de los botones y slider */
    private void configurarControles() {
        // Botón LED — alterna encendido/apagado
        btnLed.setOnClickListener(v -> {
            ledActivo = !ledActivo;
            enviarConfig();
        });

        // Botón Monitoreo — activa/pausa el envío de datos
        btnMonitoreo.setOnClickListener(v -> {
            monitoreoActivo = !monitoreoActivo;
            enviarConfig();
        });

        // Slider de sensibilidad — ajusta el umbral de detección
        seekUmbral.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                umbral = progress + 200;
                tvUmbral.setText(String.valueOf(umbral));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Enviar al servidor solo cuando el usuario suelta el slider
                enviarConfig();
            }
        });
    }

    /** Actualiza el color y texto de los botones según estado actual */
    private void actualizarBotones() {
        runOnUiThread(() -> {
            btnLed.setText(ledActivo ? "ENCENDIDO" : "APAGADO");
            btnLed.setBackgroundTintList(androidx.core.content.res.ResourcesCompat.getColorStateList(
                getResources(),
                ledActivo ? android.R.color.holo_green_dark : android.R.color.holo_red_dark,
                null));

            btnMonitoreo.setText(monitoreoActivo ? "ACTIVO" : "PAUSADO");
            btnMonitoreo.setBackgroundTintList(androidx.core.content.res.ResourcesCompat.getColorStateList(
                getResources(),
                monitoreoActivo ? android.R.color.holo_green_dark : android.R.color.darker_gray,
                null));
        });
    }

    /** Obtiene la configuración actual del servidor (LED, monitoreo, umbral) */
    private void cargarConfig() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER + "/config");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject json = new JSONObject(sb.toString());
                ledActivo = json.getBoolean("led_activo");
                monitoreoActivo = json.getBoolean("monitoreo_activo");
                umbral = json.getInt("umbral");
                runOnUiThread(() -> {
                    seekUmbral.setProgress(umbral - 200);
                    tvUmbral.setText(String.valueOf(umbral));
                    actualizarBotones();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error cargando config: " + e.getMessage());
            }
        }).start();
    }

    /** Envía la configuración actualizada al servidor */
    private void enviarConfig() {
        actualizarBotones();
        new Thread(() -> {
            try {
                URL url = new URL(SERVER + "/config");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String body = "{\"led_activo\":" + ledActivo +
                    ",\"monitoreo_activo\":" + monitoreoActivo +
                    ",\"umbral\":" + umbral + "}";
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes());
                conn.getResponseCode();
            } catch (Exception e) {
                Log.e(TAG, "Error enviando config: " + e.getMessage());
            }
        }).start();
    }

    /** Obtiene las últimas lecturas del sensor y actualiza gráfica y estado */
    private void cargarDatos() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER + "/datos");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONArray arr = new JSONArray(sb.toString());

                // Tomar últimas 30 lecturas para la gráfica
                List<Entry> entradas = new ArrayList<>();
                int inicio = Math.max(0, arr.length() - 30);
                for (int i = inicio; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    entradas.add(0, new Entry(arr.length() - i, obj.getInt("valor_analogico")));
                }

                JSONObject ultimo = arr.length() > 0 ? arr.getJSONObject(0) : null;

                runOnUiThread(() -> {
                    // Actualizar gráfica
                    dataSet.clear();
                    for (Entry e : entradas) dataSet.addEntry(e);
                    dataSet.notifyDataSetChanged();
                    lineData.notifyDataChanged();
                    grafica.notifyDataSetChanged();
                    grafica.invalidate();

                    // Actualizar tarjeta de estado
                    if (ultimo != null) {
                        try {
                            int valor = ultimo.getInt("valor_analogico");
                            boolean humo = ultimo.getBoolean("humo_detectado");
                            tvValorActual.setText(String.valueOf(valor));
                            if (!monitoreoActivo) {
                                tvEstadoIcono.setText("⏸️");
                                tvEstadoTexto.setText("Monitoreo pausado");
                                findViewById(R.id.cardEstado).setBackgroundColor(Color.parseColor("#161b22"));
                            } else if (humo) {
                                tvEstadoIcono.setText("⚠️");
                                tvEstadoTexto.setText("¡HUMO DETECTADO!");
                                findViewById(R.id.cardEstado).setBackgroundColor(Color.parseColor("#3d1a1a"));
                            } else {
                                tvEstadoIcono.setText("✅");
                                tvEstadoTexto.setText("Aire Limpio");
                                findViewById(R.id.cardEstado).setBackgroundColor(Color.parseColor("#161b22"));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parseando datos: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error cargando datos: " + e.getMessage());
            }
        }).start();
    }

    /** Solicita permisos de notificaciones y pantalla completa */
    private void pedirPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (!nm.canUseFullScreenIntent()) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detener actualizaciones al cerrar la app
        handler.removeCallbacks(actualizarRunnable);
    }
}
