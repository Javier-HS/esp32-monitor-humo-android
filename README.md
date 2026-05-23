# 🔥 Monitor de Humo ESP32 — App Android

App Android para monitoreo de humo en tiempo real con ESP32, sensor MQ-2, servidor AWS EC2 y Firebase Cloud Messaging.

## 📱 Características

- **Alertas emergentes** — la app emerge automáticamente al detectar humo, incluso con el celular bloqueado
- **Sonido adaptativo** — pitido más rápido según el nivel de concentración de humo
- **Vibración continua** — hasta que el usuario reconoce la alarma
- **Estado en tiempo real** — muestra el nivel del sensor actualizado cada 5 segundos
- **Gráfica de nivel** — historial de las últimas 30 lecturas
- **Control remoto** — enciende/apaga el LED del ESP32 desde la app
- **Control de monitoreo** — activa o pausa el envío de datos
- **Sensibilidad ajustable** — slider para configurar el umbral de detección (200–3000)
- **Notificaciones push** — mediante Firebase Cloud Messaging (FCM)

## 🏗️ Arquitectura
