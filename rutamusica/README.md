# RutaMúsica

Reproductor de música de YouTube con letra tipo karaoke para la pantalla Android 5.1 del carro.

- **Cuenta:** inicio de sesión de Google para TV (código en google.com/device) con permiso de solo lectura
  (`youtube.readonly`). Requiere una llave OAuth propia del tipo "TV y dispositivos de entrada limitada"
  con la YouTube Data API v3 activada; la llave se escribe en la app (no va en el APK ni en el repo).
- **Listas:** "Me gusta" y las listas propias, vía YouTube Data API v3.
- **Reproducción:** reproductor oficial de YouTube (IFrame Player API) en un WebView, siempre visible.
  No se extrae ni descarga audio. Se pausa al salir de la app (reglas de YouTube).
- **Letra:** LRCLIB (lrclib.net), letra sincronizada con resaltado tipo karaoke; ajuste de ±0.5 s y
  búsqueda manual por canción.
- **Volante:** MediaSession para siguiente/anterior/pausa.
- **Compilación:** `.github/workflows/build-rutamusica.yml` publica la release `rutamusica-latest`
  (rutamusica.apk + version.json) que usa el actualizador de Ajustes.
