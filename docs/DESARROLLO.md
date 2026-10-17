# Desarrollo

## Requisitos

- **Android Studio** (o solo el Android SDK) con la plataforma de API 35.
- **JDK 21**. Gradle 8.11.1 no funciona con Java 25, que es el que trae Android Studio 2026. Se puede usar el JDK 21 que Studio descarga en `~/.jdks/`.

## Compilar

```bash
# Windows (PowerShell)
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jbr-21.0.11"
.\gradlew.bat assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat installDebug       # instala en el móvil conectado
```

```bash
# macOS / Linux
export JAVA_HOME=~/.jdks/jbr-21.0.11
./gradlew assembleDebug
```

## Versión firmada (release)

1. Crea la clave de firma una sola vez (guárdala, junto con su contraseña, en un lugar seguro: sin ella no se puede actualizar la app instalada):

   ```bash
   keytool -genkeypair -v -keystore launcherfacil.jks -keyalg RSA -keysize 4096 -validity 10000 -alias launcherfacil
   ```

2. Crea `keystore.properties` en la raíz del proyecto (está en `.gitignore`, nunca se sube):

   ```properties
   storeFile=/ruta/a/launcherfacil.jks
   storePassword=...
   keyAlias=launcherfacil
   keyPassword=...
   ```

3. Compila:

   ```bash
   ./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
   ```

Sin `keystore.properties`, el APK de release sale sin firmar y no se puede instalar.

Con `keystore.properties`, **la versión debug también se firma con esa clave**: así se puede instalar encima de la versión final para seguir probando en el mismo móvil (sin él, se usa la clave de depuración de Android).

> Un APK de debug y uno de release tienen firmas distintas: no se puede instalar uno encima del otro.

## Estructura

| Carpeta (`app/src/main/java/com/launcherfacil/app/`) | Contenido |
|---|---|
| `MainActivity.kt` | Navegación entre pantallas y permisos. |
| `ui/` | Pantallas en Jetpack Compose: principal, contactos, WhatsApp, teclado, mensajes, llamadas perdidas, PIN, panel del cuidador y pantalla de llamada. |
| `call/` | App de Teléfono: `CallService` (`InCallService`), `CallActivity`, tono y vibración propios. |
| `whatsapp/` | Llamadas entrantes de WhatsApp leídas de sus notificaciones (`NotificationListenerService`). |
| `contacts/` | Favoritos de la agenda, fotos y filas de videollamada de WhatsApp. |
| `kiosk/` | Device Owner y modo quiosco (Lock Task, restricciones, apps ocultas, actualizaciones). |
| `sos/` | SOS: ubicación puntual, SMS y servicio en primer plano. |
| `locate/` | Petición de ubicación por SMS con palabra clave. |
| `inbox/` | SMS recibidos y llamadas perdidas. |
| `volume/` | Botones de volumen y panel grande. |
| `config/` | Ajustes del cuidador (PIN con hash y sal, contactos, palabras clave). |

## Herramientas de depuración (solo en la versión debug)

```bash
# Salida de emergencia: quita el modo quiosco y el Device Owner (por si se olvida el PIN)
adb shell am broadcast -a com.launcherfacil.app.DEBUG_EXIT_KIOSK -p com.launcherfacil.app

# Fotos de prueba para los favoritos: usa <nombre>.jpg (en minúsculas y sin tildes) de
# /sdcard/Android/data/com.launcherfacil.app/files/fotos/ si existe; si no, dibuja una cara
adb shell am broadcast -a com.launcherfacil.app.DEBUG_TEST_PHOTOS -p com.launcherfacil.app

# SOS con intervalos de 15 s en lugar de 10 min
adb shell am start -S -n com.launcherfacil.app/.MainActivity --ei debug_interval_seconds 15
```

Para probar las videollamadas entrantes de WhatsApp en el **emulador**, donde no hay WhatsApp real, hace falta una app que publique una notificación de llamada con `Notification.CallStyle` y el paquete `com.whatsapp`. Nunca la instales en un móvil con el WhatsApp de verdad: chocaría con él.
