# RutaTV

Reproductor IPTV propio para la pantalla del carro (Android 5.1 / API 22 o superior),
en horizontal y con botones grandes. Uso personal.

## Qué hace

- **Perfiles**: al abrir pregunta quién va a ver. Cada perfil tiene sus credenciales Xtream,
  sus favoritos, sus recientes y su progreso (películas y episodios continúan donde quedaron).
- **TV en vivo, Películas y Series** desde la API Xtream estándar (`player_api.php`):
  categorías a la izquierda, contenido a la derecha, búsqueda en todo el catálogo.
- **Favoritos**: se importan (solo lectura) los de VivaPlayer de cada perfil y se pueden
  marcar más con la ☆; esos nuevos se guardan solo en el aparato.
- **Reproductor** (Media3/ExoPlayer): ‹ › para cambiar de canal o de episodio, ☆ favorito,
  ⤢ ajustar/llenar/estirar, reconexión automática en vivo (prueba .ts y .m3u8).
- **Actualizador**: Ajustes → *Buscar actualización* → *Descargar e instalar*.

## Configuración (primera vez)

Dos opciones:

1. **Importar de VivaPlayer**: token de la API de revendedor + tu usuario de cliente.
   La app busca *solo* ese usuario, crea un perfil por cada perfil Xtream suyo e importa
   sus favoritos. Otros clientes de la cuenta de revendedor se descartan sin mostrarse.
   El token no va dentro del APK; solo se guarda en el aparato si marcas «Recordar token».
2. **Perfil manual**: servidor, usuario y contraseña Xtream (o pegar un enlace M3U `get.php`).

Si la importación no encuentra algo, *Ver detalles técnicos* muestra la respuesta de la API
(solo la del usuario elegido) para poder ajustar la app.

## Compilar y publicar una actualización

Cada push a esta carpeta en la rama de trabajo dispara `.github/workflows/build-rutatv.yml`:
compila el APK, usa el número de build como `versionCode` y publica la release
`rutatv-latest` con `rutatv.apk` y `version.json`. La app consulta ese `version.json`;
si el número es mayor que el instalado, ofrece descargar e instalar.

El APK se firma con `app/rutatv-debug.keystore` (llave de depuración fija, contraseña
`android`) para que cada versión se instale encima de la anterior sin borrar datos.
Es una llave solo para uso personal, no para publicar en tiendas.
