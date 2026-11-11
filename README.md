# Auto Hotspot BT

App Android (Kotlin + Jetpack Compose, API 26+) que **enciende la Zona WiFi y apaga el Wi-Fi
cuando el móvil se conecta a un dispositivo Bluetooth concreto** (p. ej. el manos libres del
coche) y lo revierte al desconectarse.

## Cómo abrirlo

1. Android Studio → **File › Open** → selecciona la carpeta `auto-hotspot-bt`.
2. Deja que sincronice Gradle (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, JDK 17).
3. Ejecuta en un móvil **real** (el emulador no tiene Bluetooth ni tethering reales).

## Si tu móvil rechaza el permiso (error «code 14», típico de Xiaomi/HyperOS)

Algunos fabricantes no aceptan «Modificar ajustes del sistema» para la Zona WiFi. La app integra
**Shizuku**, que ejecuta las llamadas con la identidad de `adb shell` (tiene `TETHER_PRIVILEGED`
y `NETWORK_SETTINGS`). Con Shizuku la app también puede **apagar y encender el Wi-Fi**.

1. Instala Shizuku desde Play Store.
2. Ábrelo y pulsa **Iniciar** en «Iniciar mediante depuración inalámbrica» (sigue el
   emparejamiento la primera vez; en Xiaomi activa antes *Opciones de desarrollador*).
3. Vuelve a Auto Hotspot BT y pulsa **Autorizar** en la fila Shizuku.

Sin root, Shizuku hay que volver a iniciarlo tras cada reinicio del teléfono.

## Primer uso

1. Concede **Bluetooth** y **Notificaciones**.
2. Concede **«Modificar ajustes del sistema»** (botón *Abrir ajustes* → activar el interruptor).
3. (Recomendado) Quita la **optimización de batería** a la app.
4. Elige el manos libres en la lista de dispositivos vinculados.
5. Pulsa **«Encender hotspot»** en la tarjeta *Probar*: así sabes si tu móvil acepta el control
   automático antes de ir al coche.
6. Activa el **switch del servicio**.

## Estructura

```
app/src/main/java/com/autohotspot/
├── AutoHotspotApp.kt                  # Application: desbloqueo de APIs ocultas + canales
├── bluetooth/
│   ├── BluetoothConnectionReceiver.kt # ACL_CONNECTED / ACL_DISCONNECTED (manifiesto)
│   └── BluetoothHelper.kt             # Vinculados + "¿está conectado ahora?"
├── data/SettingsRepository.kt         # Configuración y estado (SharedPreferences + Flow)
├── network/
│   ├── TetheringController.kt         # Encender/apagar Zona WiFi (API oculta, 8–15)
│   └── WifiController.kt              # Wi-Fi cliente (limitado en Android 10+)
├── receiver/BootReceiver.kt           # Reanuda tras reinicio/actualización
├── service/HotspotForegroundService.kt# Servicio en primer plano "connectedDevice"
├── ui/ (MainActivity, MainScreen, MainViewModel, theme/)
└── util/ (Permissions, Notifications, SystemIntents)
```

Flujo: `BluetoothConnectionReceiver` → `HotspotForegroundService` (serializa con Mutex,
15 s de gracia antes de revertir) → `TetheringController` / `WifiController`.

## Lo que Android permite y lo que no (léelo)

| Acción | Android 8–9 | Android 10 | Android 11–15 |
|---|---|---|---|
| Detectar conexión BT en segundo plano | ✅ | ✅ | ✅ (12+: BLUETOOTH_CONNECT) |
| Apagar/encender Wi-Fi | ✅ `setWifiEnabled` | ❌ solo sistema | ❌ solo sistema |
| Encender Zona WiFi con internet | ⚠️ API oculta + WRITE_SETTINGS | ⚠️ ídem | ⚠️ `TetheringManager` oculto + WRITE_SETTINGS |

- **Zona WiFi**: no existe API pública. `startLocalOnlyHotspot()` sí es pública pero crea una
  red **sin internet** con nombre aleatorio, así que no sirve. La app llama a la API interna
  (`ConnectivityManager.startTethering` en 8–10, `TetheringManager.startTethering` en 11+)
  mediante reflexión. AOSP acepta la llamada si la app tiene `TETHER_PRIVILEGED` **o**
  `WRITE_SETTINGS`. Funciona en muchos móviles, pero algunos fabricantes u operadores
  (comprobación de "entitlement"/aprovisionamiento) lo bloquean.
- **Wi-Fi**: desde Android 10 una app normal no puede apagarlo. En la práctica Android lo apaga
  solo al encender la Zona WiFi (salvo móviles con Wi-Fi + hotspot simultáneos) y lo restaura
  al apagarla. Si no pasa, la app muestra una notificación que abre el panel de Wi-Fi.
- **Fallback garantizado**: si algo falla, aparece una notificación de alta prioridad
  «Toca para activar la Zona WiFi» que abre directamente el ajuste. Desde Android 10 un servicio
  no puede abrir pantallas por sí solo; la notificación es la vía permitida.

### Alternativas si tu móvil bloquea la API

1. **Root / Magisk**: instala la app como *priv-app* (`/system/priv-app` con un módulo Magisk)
   y concede `TETHER_PRIVILEGED` en un archivo de privapp-permissions. Ya está declarado en el
   manifiesto y `TetheringController` lo detecta. También permite `setWifiEnabled`.
2. **Shizuku** (sin root, se activa por depuración inalámbrica): la identidad `shell` tiene
   `TETHER_PRIVILEGED`. Se puede añadir una estrategia que llame a `TetheringManager` a través
   de `ShizukuBinderWrapper`. Es la mejor opción sin root si la API falla en tu móvil.
3. **Device Owner** (móvil dedicado al coche, se configura con `adb` sobre un móvil sin
   cuentas): más control de red y sin restricciones de segundo plano.
4. **Rutinas del sistema**: Samsung *Modos y rutinas* y Xiaomi/Pixel con Tasker permiten la
   misma automatización sin código; útil si solo quieres la función y no la app.

### Optimizaciones de fabricante

Xiaomi, Huawei, Oppo, Samsung… matan servicios en segundo plano. Además de quitar la
optimización de batería, en Xiaomi activa **Inicio automático** y en Samsung añade la app a
**Apps que nunca se suspenden**. Ver https://dontkillmyapp.com.

## Permisos declarados y por qué

- `BLUETOOTH`/`BLUETOOTH_ADMIN` (≤ API 30) y `BLUETOOTH_CONNECT` (API 31+): lista de vinculados
  y recepción de eventos ACL.
- **No** se usan `BLUETOOTH_SCAN` ni `ACCESS_FINE_LOCATION`: la app no escanea, solo usa
  dispositivos ya vinculados, así que no necesita ubicación.
- `ACCESS/CHANGE_WIFI_STATE`, `ACCESS/CHANGE_NETWORK_STATE`: estado y control de red.
- `WRITE_SETTINGS`: permiso especial que habilita el tethering para apps no-sistema.
- `TETHER_PRIVILEGED`: solo se concede a apps de sistema; declarado para el caso root/priv-app.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` (Android 14+), `POST_NOTIFICATIONS`
  (Android 13+), `RECEIVE_BOOT_COMPLETED`.

## Google Play

El uso de APIs ocultas y `WRITE_SETTINGS` puede dar problemas en la revisión de Google Play. Para
uso personal (instalación directa del APK) no hay ningún problema.
