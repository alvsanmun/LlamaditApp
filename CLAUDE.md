# Launcher Fácil — contexto del proyecto

Launcher Android para que **personas analfabetas** usen un móvil solo con imágenes. Lo configura un cuidador. El usuario (desarrollador) habla en **español** y quiere avanzar **paso a paso, probando cada parte** antes de seguir.

## Requisitos

1. Interfaz solo con imágenes y botones grandes. Texto mínimo.
2. **Llamadas**: tocar la foto de un contacto para llamar, y teclado para marcar un número a mano.
3. **WhatsApp**: solo **videollamada**, tocando la foto del contacto (las llamadas de voz se hacen por teléfono normal).
4. **Botón SOS** para emergencias.
5. Pantalla principal con los botones anteriores, el **reloj en grande** y el **estado de la batería en grande**.
6. **Modo quiosco**: la persona no puede salir del launcher y el móvil siempre arranca con él.
7. **Localización bajo demanda**: otro móvil puede ver dónde está el teléfono, pero solo cuando lo pide. Nada de seguimiento continuo.
8. Dispositivo físico de pruebas: **Samsung Galaxy A16** (aún no disponible el 2026-10-02).

## Decisiones técnicas acordadas

| Requisito | Solución |
|---|---|
| Lenguaje y UI | Kotlin + Jetpack Compose, minSdk 26, compile/targetSdk 35 |
| Modo quiosco | App como **Device Owner** (`adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver`) + **Lock Task Mode**, con `setLockTaskPackages` permitiendo WhatsApp y el marcador/telecom. Requiere quitar todas las cuentas del móvil (Google/Samsung) o restablecerlo de fábrica. Probarlo antes en el emulador. |
| Arranque con el launcher | `intent-filter` HOME + DEFAULT; como Device Owner, `addPersistentPreferredActivity`. |
| Llamadas | `ACTION_CALL` con permiso `CALL_PHONE`. |
| WhatsApp | No hay API oficial. Buscar en `ContactsContract.Data` las filas con mimetype `vnd.android.cursor.item/vnd.com.whatsapp.video.call` (vídeo; la de voz, `...voip.call`, no se usa), y lanzar `ACTION_VIEW` sobre `content://com.android.contacts/data/<id>` con el paquete `com.whatsapp`. El contacto debe estar guardado en la agenda y tener WhatsApp. |
| SOS | Mantener pulsado 2 s (con un anillo de progreso). Llama **en el acto** al contacto de confianza y, en paralelo, `SosService` (servicio en primer plano de tipo location) le envía un SMS con un enlace de Google Maps y luego **3 actualizaciones cada 10 min** (30 min en total). Después se para solo; no hay palabra clave para pararlo. No se usa WhatsApp: no tiene API para enviar mensajes ni para compartir la ubicación en tiempo real (descartado, 2026-10-03). Android **no permite** que una app de terceros llame directamente al 112 (solo abre el marcador con el número, y hace falta un toque más). |
| Localización bajo demanda | Un móvil autorizado envía un **SMS con palabra clave**. El launcher (con `RECEIVE_SMS`) comprueba el remitente, obtiene una posición GPS puntual y responde por SMS con un enlace de Google Maps y el nivel de batería. Sin servidor. |
| App acompañante | **Descartada por ahora** (2026-10-03). Los cuidadores usan Android e iPhone, y en iPhone una app no puede leer ni enviar SMS sola: sin servidor no aportaría casi nada. La alarma fuerte del SOS se consigue configurando el contacto en el móvil del cuidador (ver la puesta en marcha). Si algún día se quiere la ubicación en vivo o alertas push, haría falta Firebase (proyecto con la cuenta del usuario, plan Blaze y datos móviles en el A16). |
| Configuración | Acceso oculto: **mantener pulsado el reloj 5 s** → PIN de 4 cifras (la primera vez se crea, dos veces) → pantalla del cuidador. Los contactos y las fotos se gestionan en la app Contactos del sistema (favoritos ★), que se abre desde ahí pausando el quiosco. |

## Plan por pasos (estado)

1. ✅ Entorno + app "Hola" funcionando (verificado en el emulador el 2026-10-02).
2. ✅ Pantalla principal: reloj, batería y botones grandes (los botones aún no hacen nada).
3. ✅ Llamadas: **3a** contactos con foto y teclado numérico. **3b** SOS.
4. ✅ Videollamadas de WhatsApp (solo vídeo, sin voz) (probado en el emulador con un WhatsApp falso; falta probarlo con el real en el A16).
5. ✅ Localización bajo demanda por SMS (probado en el emulador, también con la pantalla apagada).
6. ⏳ Modo quiosco + arranque automático: ✅ en el emulador; falta el A16.
7. ✅ Pantalla de configuración del cuidador con PIN (probado en el emulador).

## Estado del código

- Paquete / applicationId: `com.launcherfacil.app`, nombre "Launcher Fácil".
- `MainActivity.kt`: edge-to-edge con barras oscuras y muestra `HomeScreen`.
- `MainActivity.kt` → `LauncherApp()`: navegación simple con `enum Screen` (Home, Contacts, WhatsApp, Dialpad); el botón Atrás vuelve a Home y, **tras lanzar una llamada o videollamada, la app vuelve a Home** (al colgar se ve la pantalla principal). Pide `READ_CONTACTS` y `CALL_PHONE` al arrancar.
- `ui/HomeScreen.kt`: reloj, batería y cuadrícula 2×2 (Llamar verde, videollamada de WhatsApp verde azulado, Teclado azul, SOS rojo).
- `ui/ContactsScreen.kt`: cuadrícula de 2 columnas con los contactos **favoritos (★) de la agenda** y su foto (o una silueta si no tienen). Tocar = llamar. El nombre aparece pequeño, para el cuidador.
- `contacts/Contacts.kt`: `loadFavoriteContacts` (un número por contacto, el principal si lo hay) y `loadContactPhoto`.
- `ui/DialpadScreen.kt`: teclado 1‑9/0, borrar (pulsación larga = todo) y llamar (mínimo 3 cifras: con menos, Android lo trata como USSD).
- `ui/SosButton.kt`: pulsación larga de 2 s con anillo. `sos/SosService.kt`: secuencia de SMS (`UPDATES = 3`, `INTERVAL_MS = 10 min`). `sos/Location.kt`: posición puntual (GPS 20 s → red → última conocida). `sos/Sms.kt`: `sendSms` multiparte.
- Contacto SOS: el elegido en la configuración del cuidador o, si no hay ninguno elegido, el **primer favorito por orden alfabético**. Si no hay favoritos, abre el marcador con el 112.
- Permisos: contactos, llamada, SMS, **`READ_PHONE_STATE`** (en Android reciente `sendMultipartTextMessage` lanza `SecurityException: getGroupIdLevel1` sin él), ubicación y notificaciones.
- Probar el SOS rápido (solo en debug): `adb shell am start -S -n com.launcherfacil.app/.MainActivity --ei debug_interval_seconds 15`, mantener pulsado SOS y consultar `adb shell content query --uri content://sms/sent`. Mover la ubicación: `adb emu geo fix <lon> <lat>`.
- `ui/WhatsAppScreen.kt`: favoritos (★) que tienen WhatsApp. **WhatsApp solo se usa para videollamadas** (decisión del 2026-10-03): tocar la foto inicia la videollamada; una cámara en la esquina lo indica. Las llamadas de voz van por teléfono normal. Si WhatsApp no está instalado, o ningún favorito lo tiene, se muestra un mensaje para el cuidador. `contacts/WhatsApp.kt`: consulta de `ContactsContract.Data` y apertura con `ACTION_VIEW` + `setPackage("com.whatsapp")`. El manifiesto declara `<queries>` para `com.whatsapp`. No se admite WhatsApp Business (`com.whatsapp.w4b`).
- **Localización bajo demanda** (`locate/`): `SmsReceiver` (broadcast `SMS_RECEIVED`, protegido con `BROADCAST_SMS`) → si el texto es una palabra clave (`UBICACION` o `DONDE ESTAS`, sin importar tildes, mayúsculas ni signos) y el remitente es un **favorito (★)** → `LocateService` (servicio en primer plano de tipo location) obtiene una posición puntual y responde por SMS con el enlace de Google Maps. Los demás remitentes se ignoran sin responder, y en el móvil no se muestra nada. Comparación de números: coinciden si las 9 últimas cifras son iguales, o si Android dice que son el mismo número (así +34600… = 600…; en el emulador, cuyo país es "us", la comparación de Android sola fallaba).
- **Ubicación en segundo plano**: con la pantalla apagada, Android solo deja usar el GPS si la app tiene `ACCESS_BACKGROUND_LOCATION` ("Permitir siempre"). Sin ese permiso, `LocateService` no puede pasar a primer plano y responde "No se ha podido obtener la ubicación ahora mismo". **En el paso 6 (Device Owner) hay que concederlo automáticamente** con `setPermissionGrantState`. Para probar en el emulador: `adb shell pm grant com.launcherfacil.app android.permission.ACCESS_BACKGROUND_LOCATION`.
- **Batería en los SMS**: todos los SMS (SOS y respuestas de ubicación) terminan en " · Batería 45 %" o " · Batería 80 % (cargando)" (`sos/BatteryText.kt`, `withBattery`). Lee el mismo broadcast `ACTION_BATTERY_CHANGED` que el indicador de la pantalla, así que en el emulador respeta `dumpsys battery set` (`BatteryManager.getIntProperty` lee el sensor real y lo ignoraba).
- `sos/Foreground.kt`: `startLocationForeground`, compartido por `SosService` y `LocateService`; devuelve false si Android no lo permite.
- Probar la petición por SMS en el emulador: `adb emu sms send 600111222 "¿Dónde estás?"` y consultar `adb shell content query --uri content://sms/sent`.
- **Modo quiosco** (`kiosk/`): `AdminReceiver` (Device Owner) y `Kiosk`. `Kiosk.configure()` se ejecuta en cada `onCreate`, antes de la interfaz, y solo actúa si la app es Device Owner:
  - `addPersistentPreferredActivity` HOME → MainActivity;
  - `setLockTaskPackages` con la app, WhatsApp, el marcador por defecto y las pantallas de llamada de Google y Samsung (`com.android.server.telecom`, `com.android.phone`, `com.google.android.dialer`, `com.samsung.android.dialer`, `com.samsung.android.incallui`);
  - `setLockTaskFeatures` = SYSTEM_INFO + GLOBAL_ACTIONS (barra de estado y menú de apagado; sin Inicio, Recientes ni notificaciones);
  - `setKeyguardDisabled(true)`: sin pantalla de bloqueo al encender (solo funciona si el móvil no tiene PIN, patrón ni contraseña; la salida de emergencia lo reactiva);
  - concede todos los permisos de la app (incluida `ACCESS_BACKGROUND_LOCATION`) y CAMERA, RECORD_AUDIO y READ_CONTACTS a WhatsApp, porque los diálogos de permisos no pueden abrirse dentro del quiosco.
  `Kiosk.enterLockTask()` se llama en `onResume`. MainActivity es `singleTask` con HOME/DEFAULT además de LAUNCHER.
- **Salida de emergencia (solo en debug)**: `adb shell am broadcast -a com.launcherfacil.app.DEBUG_EXIT_KIOSK -p com.launcherfacil.app` → sale del Lock Task, borra el HOME persistente y deja de ser Device Owner (`src/debug/.../DebugExitKioskReceiver.kt`, protegido con el permiso DUMP; llama a `Kiosk.disable`). Sirve por si se olvida el PIN. En release, la salida es el botón "Desactivar el modo quiosco" de la configuración del cuidador. **Sin una de las dos, un Device Owner solo se quita con un restablecimiento de fábrica.**
- Probado en el emulador (2026-10-03): Inicio, Recientes y Ajustes bloqueados; llamadas, SOS, teclado y WhatsApp (falso) funcionan dentro del quiosco; tras reiniciar arranca solo y bloqueado; la petición de ubicación por SMS funciona con la pantalla apagada y sin `pm grant`.
- Durante las pruebas con adb conviene `adb shell svc power stayon true`; si la pantalla se apaga, los `input tap` no hacen nada.
- **Configuración del cuidador** (paso 7):
  - `config/CaregiverSettings.kt` (SharedPreferences "caregiver"): PIN guardado como SHA-256 con sal (nunca en claro); `sosContactId` (null = primer favorito); `locateContactIds` (null = todos los favoritos); `keywords` (por defecto UBICACION y DONDE ESTAS). Funciones `resolveSosContact()` y `locateContacts()`, usadas por el SOS y por `locate/`.
  - `HomeScreen`: mantener pulsado el reloj 5 s (`Modifier.holdFor`, sin respuesta visual, solo vibración) → `Screen.Pin` (o `Screen.NewPin` si aún no hay PIN).
  - `ui/PinScreen.kt`: teclado reutilizado de `DialpadScreen` (`KeyRow`, `DigitKey` y `KeyBox` son `internal`); en modo creación pide el PIN dos veces.
  - `ui/SettingsScreen.kt` (Material3 oscuro, con texto): contacto SOS (radio), quién puede pedir la ubicación (casillas), palabras clave (separadas por comas), cambiar el PIN, "Abrir Contactos" y "Abrir Ajustes de Android" (`Kiosk.pauseAndOpen`: sale del Lock Task, que vuelve en el siguiente `onResume` al regresar al launcher) y "Desactivar el modo quiosco" (con confirmación → `Kiosk.disable`).
  - Al apagar la pantalla (`ACTION_SCREEN_OFF`), el PIN y la configuración se cierran y se vuelve a Home.
  - `AdminReceiver.onEnabled`: al hacer `dpm set-device-owner` aplica el quiosco y abre el launcher, que se bloquea sin reiniciar nada.
  - **PIN de pruebas en el emulador: 1234.** En el emulador, el SOS está asignado a María y Juan no puede pedir la ubicación.
- `ui/ContactPhoto.kt`: `ContactPhoto` (foto + nombre) y `CaregiverMessage`, compartidos por las pantallas de Contactos y WhatsApp.
- Módulo **`whatsapp-stub/`**: WhatsApp falso (paquete `com.whatsapp`) **solo para el emulador**; muestra qué intent recibe. Solo se incluye en el build con `-PwithWhatsAppStub`: `gradlew -PwithWhatsAppStub :whatsapp-stub:installDebug`. Un `installDebug` normal no lo instala. **Nunca en un móvil con el WhatsApp real** (chocaría con el paquete). En el emulador, María y Lucía tienen filas de WhatsApp de prueba; Juan no.
- `ui/Common.kt`: `BigTile`, `TileIcon` y `HomeBar` (barra con una casa para volver). `Calls.kt`: `placeCall` (`ACTION_CALL`).
- `ui/Clock.kt` (hora H:mm 120sp + fecha según el idioma del sistema), `ui/Battery.kt` (icono dibujado con Canvas, color verde >50 %, ámbar >20 %, rojo; rayo al cargar), `ui/Colors.kt`.
- Iconos: `ic_call`, `ic_dialpad`, `ic_home`, `ic_person`, `ic_backspace`, `ic_videocam` (Material, Apache 2.0) e `ic_whatsapp_video` (bocadillo con cámara, sin el logotipo real).
- Contactos de prueba en el emulador (María, Juan y Lucía son favoritos; "Otro" no), creados con `adb shell content insert`. Aún no tienen foto: se añade desde la app Contactos.
- Probar la batería en el emulador: `adb shell dumpsys battery set level 15` / `set status 2` (cargando) / `dumpsys battery reset`.
- Versiones: AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1, Compose BOM 2024.12.01 (en `gradle/libs.versions.toml`).
- Icono provisional: vector de una casa (`res/drawable/ic_launcher.xml`).
- Git: repositorio inicializado; primer commit hecho el 2026-10-02 tras verificar el paso 1.

## Entorno de desarrollo (lecciones del PC original)

- **Android Studio 2026.2.1** trae JBR **Java 25**, y **Gradle 8.11.1 no funciona con Java 25**. Para compilar desde la terminal hay que usar JDK 21, el mismo que Studio descarga en `%USERPROFILE%\.jdks\jbr-21.x`:
  - PowerShell: `$env:JAVA_HOME="$env:USERPROFILE\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug`
  - Otra opción: migrar a Gradle 9.x / AGP 9.x, que sí admiten Java 25 (ojo: AGP 9 trae Kotlin integrado y cambia la configuración de plugins).
- `local.properties` es propio de cada PC (Studio lo regenera con `sdk.dir`). No se copia.
- Emulador del PC original (i5‑2500K de 2011 + GTX 560): **no funcionó**.
  - El primer intento con el driver AEHD falló porque Studio no se abrió como administrador.
  - Con WHPX (Plataforma del hipervisor de Windows) la aceleración se detecta, pero la CPU virtual nunca se ejecuta (error "hanging thread QEMU2 CPU0").
  - Con una GPU antigua hay que usar `-gpu swiftshader_indirect`.
  - En un PC moderno no debería haber problema.
- **PC actual** (Ryzen 7 9800X3D, Windows 11): el emulador va bien con WHPX y arranca en ~45 s.
  - AVD en uso: `Medium_Phone_API_37.0` (lo creó Studio, imagen Google Play x86_64, API 37). No hay cmdline-tools instaladas, así que los AVD se crean desde Studio.
  - Arrancarlo desde la terminal (con PowerShell `Start-Process` queda independiente; las tareas en segundo plano de Claude se cortan a los 30 min): `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Medium_Phone_API_37.0`
  - JDK 21 para Gradle: `%USERPROFILE%\.jdks\jbr-21.0.11`. Hay otro en `C:\Program Files\Android\openjdk\jdk-21.0.8`.
- Samsung A16: activar las opciones de desarrollador (tocar 7 veces "Número de compilación") y la depuración USB, e instalar el Samsung Android USB Driver en Windows.

## Siguiente paso al retomar

Los 7 pasos están hechos en el emulador. Lo siguiente es probarlo todo en el **Samsung A16** cuando llegue:
1. Opciones de desarrollador + depuración USB + Samsung USB Driver.
2. Instalar WhatsApp y comprobar que añade sus filas a la agenda; marcar los favoritos (★) con foto.
3. Quitar las cuentas de Google/Samsung (o restablecer el móvil sin añadir ninguna), dejarlo **sin PIN de bloqueo**, `gradlew installDebug` y `adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver`.
4. Probar: llamadas, teclado, videollamadas de WhatsApp (el real), SOS (que cada ubicación del SMS sea nueva), petición de ubicación con la pantalla apagada, reinicio, y los paquetes de llamada de Samsung dentro del quiosco.

Puesta en marcha en los móviles de los cuidadores (sin app; vale para iPhone y Android): guardar el número del A16 como contacto y hacer que sus llamadas y SMS suenen aunque el móvil esté en silencio. En iPhone: Contacto → Tono y Tono de mensaje → activar **"Saltar silencio"** (Emergency Bypass). En Android: marcarlo como favorito, permitirlo como excepción en "No molestar" y ponerle un tono distinto.

Ideas pendientes (sin decidir): bloquear el PIN tras varios intentos fallidos y una versión release firmada.
