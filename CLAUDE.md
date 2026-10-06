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
8. Dispositivo físico de pruebas: **Samsung Galaxy A16** (SM-A165F, Android 16 / API 36, One UI 8.5; serie adb `R5GL52BCW4N`). Llegó el 2026-10-05. SIM de **DIGI** desde el 2026-10-06, **sin PIN** (para que tras un reinicio quede operativo sin hacer nada).

## Decisiones técnicas acordadas

| Requisito | Solución |
|---|---|
| Lenguaje y UI | Kotlin + Jetpack Compose, minSdk 26, compile/targetSdk 35 |
| Modo quiosco | App como **Device Owner** (`adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver`) + **Lock Task Mode**, con `setLockTaskPackages` permitiendo WhatsApp y el marcador/telecom. Requiere quitar todas las cuentas del móvil (Google/Samsung) o restablecerlo de fábrica. Probarlo antes en el emulador. |
| Arranque con el launcher | `intent-filter` HOME + DEFAULT; como Device Owner, `addPersistentPreferredActivity`. |
| Llamadas | `ACTION_CALL` con permiso `CALL_PHONE`. La app es además la **app de Teléfono predeterminada** (`InCallService`): todas las llamadas normales, entrantes y salientes, usan nuestra pantalla (foto grande, verde/rojo de un toque). Motivo: dentro del quiosco la notificación de llamada entrante del sistema no se ve con la pantalla encendida (descubierto el 2026-10-03), y la pantalla de Samsung obliga a deslizar. |
| WhatsApp entrante | `NotificationListenerService` (`WhatsAppCallListener`) lee la notificación de llamada entrante de WhatsApp y abre nuestra pantalla; verde y rojo pulsan por dentro sus botones (PendingIntent). Necesita "acceso a notificaciones" (una vez, por adb o desde la configuración del cuidador). **Pendiente de probar con el WhatsApp real en el A16.** |
| WhatsApp saliente | No hay API oficial. Buscar en `ContactsContract.Data` las filas con mimetype `vnd.android.cursor.item/vnd.com.whatsapp.video.call` (vídeo; la de voz, `...voip.call`, no se usa), y lanzar `ACTION_VIEW` sobre `content://com.android.contacts/data/<id>` con el paquete `com.whatsapp`. El contacto debe estar guardado en la agenda y tener WhatsApp. |
| SOS | Mantener pulsado 2 s (con un anillo de progreso). Llama **en el acto** al contacto de confianza y, en paralelo, `SosService` (servicio en primer plano de tipo location) le envía un SMS con un enlace de Google Maps y luego **3 actualizaciones cada 10 min** (30 min en total). Después se para solo; no hay palabra clave para pararlo. No se usa WhatsApp: no tiene API para enviar mensajes ni para compartir la ubicación en tiempo real (descartado, 2026-10-03). Android **no permite** que una app de terceros llame directamente al 112 (solo abre el marcador con el número, y hace falta un toque más). |
| Localización bajo demanda | Un móvil autorizado envía un **SMS con palabra clave**. El launcher (con `RECEIVE_SMS`) comprueba el remitente, obtiene una posición GPS puntual y responde por SMS con un enlace de Google Maps y el nivel de batería. Sin servidor. |
| Videollamada en curso de WhatsApp | Se queda en la pantalla de WhatsApp, con sus botones. **Descartado** (2026-10-06): meter el vídeo en el launcher (imposible) y tapar sus botones con una capa propia ("colgar" grande sería viable con la acción de la notificación; "cambiar de cámara" solo con accesibilidad, frágil). |
| App acompañante | **Descartada por ahora** (2026-10-03). Los cuidadores usan Android e iPhone, y en iPhone una app no puede leer ni enviar SMS sola: sin servidor no aportaría casi nada. La alarma fuerte del SOS se consigue configurando el contacto en el móvil del cuidador (ver la puesta en marcha). Si algún día se quiere la ubicación en vivo o alertas push, haría falta Firebase (proyecto con la cuenta del usuario, plan Blaze y datos móviles en el A16). |
| Configuración | Acceso oculto: **mantener pulsado el reloj 5 s** → PIN de 4 cifras (la primera vez se crea, dos veces) → pantalla del cuidador. Los contactos y las fotos se gestionan en la app Contactos del sistema (favoritos ★), que se abre desde ahí pausando el quiosco. |

## Plan por pasos (estado)

1. ✅ Entorno + app "Hola" funcionando (verificado en el emulador el 2026-10-02).
2. ✅ Pantalla principal: reloj, batería y botones grandes (los botones aún no hacen nada).
3. ✅ Llamadas: **3a** contactos con foto y teclado numérico. **3b** SOS.
4. ✅ Videollamadas de WhatsApp (solo vídeo, sin voz) (probado en el emulador con un WhatsApp falso; falta probarlo con el real en el A16).
5. ✅ Localización bajo demanda por SMS (probado en el emulador, también con la pantalla apagada).
6. ✅ Modo quiosco + arranque automático: probado en el emulador y en el **A16** (2026-10-05).
7. ✅ Pantalla de configuración del cuidador con PIN (probado en el emulador).

## Estado del código

- Paquete / applicationId: `com.launcherfacil.app`, nombre "Launcher Fácil".
- `MainActivity.kt`: edge-to-edge con barras oscuras y muestra `HomeScreen`.
- `MainActivity.kt` → `LauncherApp()`: navegación simple con `enum Screen` (Home, Contacts, WhatsApp, Dialpad); el botón Atrás vuelve a Home y, **tras lanzar una llamada o videollamada, la app vuelve a Home** (al colgar se ve la pantalla principal). Pide `READ_CONTACTS` y `CALL_PHONE` al arrancar.
- `ui/HomeScreen.kt`: reloj, batería, cuadrícula 2×2 (Llamar verde, videollamada de WhatsApp verde azulado, Teclado azul, SOS rojo) y debajo una fila de 2 botones más pequeños (Mensajes naranja, Llamadas perdidas morado).
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
  - `setSystemUpdatePolicy` = instalación automática de **2:00 a 4:00** (el móvil se actualiza y reinicia solo);
  - `DISALLOW_BLUETOOTH`: Bluetooth apagado y bloqueado (no se usa; ahorra batería);
  - **solo 4G** (decisión del 2026-10-06): Wi-Fi apagado (`WifiManager.isWifiEnabled = false`, el Device Owner aún puede) y bloqueado (`DISALLOW_CONFIG_WIFI`, `DISALLOW_CHANGE_WIFI_STATE`), y **sin modo avión** (`DISALLOW_AIRPLANE_MODE`). En el A16 además se olvidaron las redes guardadas (`adb shell cmd wifi forget-network <id>`) y se permitió descargar las actualizaciones de Samsung por datos móviles: `adb shell settings put global SOFTWARE_UPDATE_WIFI_ONLY2 0` (ajuste propio de Samsung; persistente, no lo puede poner la app);
  - **oculta 45 apps innecesarias** (`HIDDEN_PACKAGES`, `setApplicationHidden`; lista aprobada el 2026-10-06; `disable` las vuelve a mostrar). Se mantienen Contactos, Cámara, Galería, Google Mensajes (app de SMS), Play Store, GMS, teléfono de Samsung, IMS, Knox y Encontrar mi móvil. Aplicado en el A16: 45/45;
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
- **Pantalla de llamada propia** (`call/`): `CallService` (InCallService, `IN_CALL_SERVICE_UI`; el tono lo sigue poniendo el sistema) → `CallManager` (StateFlow con la llamada visible; una entrante tiene prioridad) → `CallActivity` (encima de la pantalla de bloqueo y encendiéndola; Atrás desactivado; sensor de proximidad durante la llamada si no está el altavoz). `ui/CallScreen.kt`: `CallLayout` (foto al 90 % del ancho, nombre pequeño, duración) y `CallButton` (el verde "late"). Entrante: rojo (rechazar) | verde (contestar). En curso: altavoz | rojo grande (colgar). Busca la foto por número (`findContactByNumber`, PhoneLookup).
  - `Kiosk.configure` → `dpm.setDefaultDialerApplication(paquete)` (Android 14+). Para ser app de Teléfono, MainActivity acepta `ACTION_DIAL` (con y sin `tel:`) y abre el teclado con el número escrito (así el 112 del SOS sin favoritos sale ya marcado).
  - Comprobar: `adb shell cmd role get-role-holders android.app.role.DIALER`. Simular una llamada entrante en el emulador: `adb emu gsm call 600111222` (`adb emu gsm cancel 600111222` para cortarla).
  - **Permiso `WAKE_LOCK` obligatorio** para el sensor de proximidad: sin él la app se cerraba al descolgar en el A16 y Telecom pasaba a la pantalla de Samsung (el emulador no tiene sensor y no fallaba). `setProximity` va en try/catch para que nunca tumbe la pantalla de llamada. Probado en el A16 con una llamada real el 2026-10-06: entrante, contestar, altavoz y colgar con nuestra pantalla.
  - El emulador no tiene auricular: el audio de las llamadas siempre va al altavoz (por eso el botón sale azul). En el A16 empezará por el auricular.
- **Llamadas entrantes de WhatsApp** (`whatsapp/`): `WhatsAppCallListener` → `parseWhatsAppCall` (prefiere los extras de `Notification.CallStyle`: `android.answerIntent`, `android.declineIntent`, `android.callIsVideo`, `android.callPerson`, `android.callType`; si no, busca los botones por su texto: contestar/responder/aceptar, rechazar/colgar…) → `WhatsAppIncoming` (StateFlow) → `WhatsAppCallActivity` (misma `CallLayout`, con la cámara si es vídeo; foto de la agenda buscando el favorito por nombre, si no la de perfil de WhatsApp). Con la pantalla apagada WhatsApp abre su propia pantalla a la vez: se vuelve a traer la nuestra al frente a los 0, 0,7 y 1,5 s. Al contestar, WhatsApp abre su pantalla de videollamada (colgar con su botón rojo). **Comportamiento real de WhatsApp 2.26 (A16, 2026-10-06)**: la entrante es `CallStyle` con `callType=1`, `answerIntent` y `declineIntent` (botones "Rechazar" y "Vídeo"), más algunas copias `BigTextStyle` ("Rechazar", "Responder"); tras contestar **vuelve a publicar un instante la entrante** y luego "Videollamada en curso" (`callType=2`, solo "Colgar"). Por eso: `WhatsAppIncoming.show` ignora entrantes durante 5 s tras contestar/rechazar, y cualquier notificación de categoría `call` que no sea entrante cierra nuestra pantalla (si no, tapaba el vídeo de WhatsApp). Cada notificación de WhatsApp se vuelca al log con la etiqueta **`WhatsAppNotif`** para ajustar el reconocimiento con el WhatsApp real.
  - Activar: `adb shell cmd notification allow_listener com.launcherfacil.app/com.launcherfacil.app.whatsapp.WhatsAppCallListener` (o en la configuración del cuidador, "Activar acceso a notificaciones").
  - El WhatsApp falso simula una llamada entrante con CallStyle: `adb shell am broadcast -n com.whatsapp/com.launcherfacil.whatsappstub.SimulateCallReceiver --es name "María" --ez video true` (antes `adb shell pm grant com.whatsapp android.permission.POST_NOTIFICATIONS`).
- **Fotos de prueba (solo en debug)**: `DebugTestPhotosReceiver` dibuja una cara distinta como foto de cada favorito: `adb shell pm grant com.launcherfacil.app android.permission.WRITE_CONTACTS` y `adb shell am broadcast -a com.launcherfacil.app.DEBUG_TEST_PHOTOS -p com.launcherfacil.app`. En el emulador ya están puestas.
- **Volumen** (`volume/`, 2026-10-06): `VolumeController.onKeyEvent` se llama desde `dispatchKeyEvent` de MainActivity, CallActivity y WhatsAppCallActivity. Los botones solo cambian el **tono de llamada** (`STREAM_RING`) o, durante una llamada (`MODE_IN_CALL`/`IN_COMMUNICATION`), la voz (`STREAM_VOICE_CALL`, mínimo 1). El tono **se puede silenciar** (0 → `RINGER_MODE_VIBRATE`, nunca silencio total). Pitido corto al nuevo volumen (no en llamada ni a 0). Sin el panel del sistema: `WithVolumeOverlay` pone encima un panel grande (**diseño D**, elegido el 2026-10-06 entre 4 opciones: flecha verde ↑ o ámbar ↓ que salta, altavoz tachado rojo a 0, "escalera" de barras) y oscurece el resto 2 s. `applySoundPolicy` (al arrancar): modo de timbre según el volumen y vídeos/música (`STREAM_MUSIC`) a 0.
- **Tono propio**: `IN_CALL_SERVICE_RINGING=true` → el tono lo pone `call/CallRinger` (tono del móvil, `USAGE_NOTIFICATION_RINGTONE`, en bucle; a volumen 0 no suena, solo vibra). Motivo: con el tono del sistema, pulsar volumen mientras suena **silenciaba la llamada** (lo hace el sistema antes que la app). Ahora Android llama a `CallService.onSilenceRinger()` y se ignora a propósito: sigue sonando hasta coger o rechazar. Probado en el A16 (2026-10-06).
- **Vibración**: las llamadas normales entrantes **siempre vibran** (`call/RingVibrator`, desde `CallManager.publish` mientras haya una llamada sonando), con cualquier volumen. Dentro de la pantalla de videollamada de WhatsApp los botones los gestiona WhatsApp (panel del sistema).
- **Mensajes y llamadas perdidas** (2026-10-06): fila de dos botones más pequeños bajo los cuatro grandes (`HomeScreen`, `SmallTileWithBadge`, número rojo en la esquina):
  - **Mensajes** (naranja, `ic_sms`): `ui/InboxScreens.kt` `MessagesScreen` → lista de SMS recibidos con la foto del remitente; al tocar uno se ve en grande con una papelera. **"Borrar" solo lo quita de la lista del launcher** (`inbox/Inbox.kt` `hideSms`, guardado en SharedPreferences "inbox"): borrarlo del teléfono exige ser la app de SMS predeterminada (es Google Mensajes; serlo nosotros costaría los MMS y el RCS). Se ocultan las peticiones de ubicación. El número = SMS más nuevos que la última vez que se abrió la lista.
  - **Llamadas perdidas** (morado, `ic_phone_missed`): `MissedCallsScreen` → lista del registro de llamadas (`CallLog`, tipo perdida) con foto, nombre y hora; **tocar una devuelve la llamada**. Al abrirla se marcan como vistas (`NEW=0`, `IS_READ=1`, como app de Teléfono). El número = perdidas con `NEW=1`.
  - Los números se recalculan solos al llegar un SMS o una llamada (`rememberContentVersion`, ContentObserver). Permisos nuevos: `READ_SMS`, `READ_CALL_LOG`, `WRITE_CALL_LOG` (los concede el quiosco).
  - En la vista de un mensaje, la casa vuelve a la pantalla principal y Atrás a la lista.
- `ui/ContactPhoto.kt`: `ContactPhoto` (foto + nombre) y `CaregiverMessage`, compartidos por las pantallas de Contactos y WhatsApp.
- Módulo **`whatsapp-stub/`**: WhatsApp falso (paquete `com.whatsapp`) **solo para el emulador**; muestra qué intent recibe. Solo se incluye en el build con `-PwithWhatsAppStub`: `gradlew -PwithWhatsAppStub :whatsapp-stub:installDebug`. Un `installDebug` normal no lo instala. **Nunca en un móvil con el WhatsApp real** (chocaría con el paquete). En el emulador, María y Lucía tienen filas de WhatsApp de prueba; Juan no.
- `ui/Common.kt`: `BigTile`, `TileIcon` y `HomeBar` (barra con una casa para volver). `Calls.kt`: `placeCall` (`ACTION_CALL`).
- `ui/Clock.kt` (hora H:mm 120sp + fecha según el idioma del sistema), `ui/Battery.kt` (icono dibujado con Canvas, color verde >50 %, ámbar >20 %, rojo; rayo al cargar), `ui/Colors.kt`.
- Iconos: `ic_call`, `ic_dialpad`, `ic_home`, `ic_person`, `ic_backspace`, `ic_videocam` (Material, Apache 2.0) e `ic_whatsapp_video` (bocadillo con cámara, sin el logotipo real).
- Contactos de prueba en el emulador (María, Juan y Lucía son favoritos; "Otro" no), creados con `adb shell content insert`. Tienen fotos de prueba dibujadas (ver "Fotos de prueba").
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
- **Samsung A16, lecciones de la puesta en marcha (2026-10-05):**
  - Para activar la depuración USB hay que **desactivar el "Bloqueador automático"** (Ajustes → Seguridad y privacidad). Dejarlo desactivado mientras se desarrolle: si no, no se puede instalar por adb.
  - La app Google Meet (`com.google.android.apps.tachyon`) crea sola una cuenta "Meet" que **impide `dpm set-device-owner`**. Se quitó con `adb shell pm uninstall --user 0 com.google.android.apps.tachyon` (se recupera con `pm install-existing com.google.android.apps.tachyon`).
  - En Samsung, `DeviceAdminReceiver.onEnabled` llega **antes** de que la app sea Device Owner: `AdminReceiver` espera (hasta 9 s) a serlo antes de aplicar el quiosco. Además, `Kiosk.enterLockTask` aplica `configure()` si la app es Device Owner pero el Lock Task aún no está permitido.
  - **Una app Device Owner no se puede forzar a cerrar** (`am force-stop` no hace nada). Para que vuelva a pasar por `onCreate`, reinstalarla (`installDebug`) o reiniciar el móvil.
  - Con varios dispositivos conectados (emulador + A16), usar `ANDROID_SERIAL=R5GL52BCW4N` (o `adb -s`) para Gradle y adb.
  - **No usar "Desactivar el modo quiosco" para instalar o configurar cosas** (se usó el 2026-10-06 para instalar WhatsApp y se perdió el Device Owner): usar "Abrir Ajustes de Android", que solo pausa el quiosco. Para **reactivarlo con WhatsApp ya instalado**: quitar la cuenta de WhatsApp en Ajustes → Cuentas y copia de seguridad → Administrar cuentas (no cierra la sesión de WhatsApp ni borra chats; WhatsApp la recrea) → `pm disable-user --user 0 com.whatsapp` → `dpm set-device-owner ...` → `pm enable --user 0 com.whatsapp`. (Solo desactivar WhatsApp NO basta: su cuenta sigue contando.)
  - Los permisos de WhatsApp (cámara, micrófono, contactos) se conceden en `Kiosk.configure`, que corre al arrancar la app: **después de instalar WhatsApp, reiniciar el móvil** (o reinstalar el launcher) para que se apliquen.

## Siguiente paso al retomar

Estado del A16 (2026-10-06): Device Owner y quiosco activos; app de Teléfono = la nuestra; SIM de DIGI sin PIN; **solo 4G** (Wi-Fi apagado y bloqueado, sin modo avión); 45 apps ocultas; Bluetooth bloqueado; actualizaciones del sistema de madrugada y por datos móviles; **cuenta de Google añadida** y Play Store con actualizaciones automáticas "por cualquier red" (WhatsApp instalado desde Play Store, se actualiza solo); WhatsApp verificado con acceso a notificaciones. Probado y OK: llamadas entrantes y salientes con nuestra pantalla, teclado, SOS, ubicación por SMS, videollamada entrante de WhatsApp (contestar y rechazar), volumen y vibración.

Pendiente:
1. **Videollamada saliente de WhatsApp**: WhatsApp 2.26 no escribe sus filas `vnd.com.whatsapp.video.call` en la agenda (0 filas, aunque sabe que Alvaro y Cristina tienen WhatsApp; forzar la sincronización con `adb shell requestsync -n WhatsApp -t com.whatsapp -a com.android.contacts` no sirvió). Comprobar si aparecen con el tiempo; si no, la alternativa oficial es abrir el chat (`https://wa.me/<número>` con el paquete `com.whatsapp`) y que la persona toque la cámara. Descartada la automatización con accesibilidad salvo que el usuario la pida.
2. Que el usuario pruebe en el A16 los botones de mensajes y llamadas perdidas (en el emulador funcionan).
3. Si se desactiva el quiosco del todo, para reactivarlo hay que quitar las cuentas (Google y WhatsApp) antes de `dpm set-device-owner` (ver lecciones del A16).

Puesta en marcha en los móviles de los cuidadores (sin app; vale para iPhone y Android): guardar el número del A16 como contacto y hacer que sus llamadas y SMS suenen aunque el móvil esté en silencio. En iPhone: Contacto → Tono y Tono de mensaje → activar **"Saltar silencio"** (Emergency Bypass). En Android: marcarlo como favorito, permitirlo como excepción en "No molestar" y ponerle un tono distinto.

Ideas aparcadas por decisión del usuario (2026-10-05): bloquear el PIN tras varios intentos fallidos, versión release firmada, filtrar llamadas de números desconocidos y SOS al 112 directo.
