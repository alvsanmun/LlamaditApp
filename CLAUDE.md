# Launcher Fácil — contexto del proyecto

Launcher Android para que **personas analfabetas** usen un móvil solo con imágenes. Lo configura un cuidador. El usuario (desarrollador) habla en **español** y quiere avanzar **paso a paso, probando cada parte** antes de seguir.

## Requisitos

1. Interfaz solo con imágenes y botones grandes. Texto mínimo.
2. **Llamadas**: tocar la foto de un contacto para llamar, y teclado para marcar un número a mano.
3. **WhatsApp**: llamada de voz y videollamada tocando la foto del contacto.
4. **Botón SOS** para emergencias.
5. Pantalla principal con los botones anteriores, el **reloj en grande** y el **estado de la batería en grande**.
6. **Modo quiosco**: la persona no puede salir del launcher y el móvil siempre arranca con él.
7. **Localización bajo demanda**: otro móvil puede ver dónde está el teléfono, pero solo cuando lo pide. Nada de seguimiento continuo.
8. Dispositivo físico de pruebas: **Samsung Galaxy A16** (aún no disponible el 2026-10-02).

## Decisiones técnicas acordadas

| Requisito | Solución |
|---|---|
| Lenguaje y UI | Kotlin + Jetpack Compose, minSdk 26, compile/targetSdk 35 |
| Modo quiosco | App como **Device Owner** (`adb shell dpm set-device-owner com.launcherfacil.app/.AdminReceiver`) + **Lock Task Mode**, con `setLockTaskPackages` permitiendo WhatsApp y el marcador/telecom. Requiere quitar todas las cuentas del móvil (Google/Samsung) o restablecerlo de fábrica. Probarlo antes en el emulador. |
| Arranque con el launcher | `intent-filter` HOME + DEFAULT; como Device Owner, `addPersistentPreferredActivity`. |
| Llamadas | `ACTION_CALL` con permiso `CALL_PHONE`. |
| WhatsApp | No hay API oficial. Buscar en `ContactsContract.Data` las filas con mimetype `vnd.android.cursor.item/vnd.com.whatsapp.voip.call` (voz) y `vnd.android.cursor.item/vnd.com.whatsapp.video.call` (vídeo), y lanzar `ACTION_VIEW` sobre `content://com.android.contacts/data/<id>` con el paquete `com.whatsapp`. El contacto debe estar guardado en la agenda y tener WhatsApp. |
| SOS | Llamar a un contacto de confianza y enviarle un SMS con la ubicación. Android **no permite** que una app de terceros llame directamente al 112 (solo abre el marcador con el número, y hace falta un toque más). |
| Localización bajo demanda | Un móvil autorizado envía un **SMS con palabra clave**. El launcher (con `RECEIVE_SMS`) comprueba el remitente, obtiene una posición GPS puntual y responde por SMS con un enlace de Google Maps. Sin servidor. Más adelante, quizá una app acompañante con Firebase. |
| Configuración | Pantalla oculta para el cuidador, protegida con PIN, donde se añaden contactos y fotos. |

## Plan por pasos (estado)

1. ✅ Entorno + app "Hola" funcionando (verificado en el emulador el 2026-10-02).
2. ⬜ Pantalla principal: reloj, batería y botones grandes.
3. ⬜ Llamadas: contactos con foto, teclado numérico y SOS.
4. ⬜ Llamadas y videollamadas de WhatsApp.
5. ⬜ Localización bajo demanda por SMS.
6. ⬜ Modo quiosco + arranque automático (primero en el emulador, luego en el A16).
7. ⬜ Pantalla de configuración del cuidador con PIN.

## Estado del código

- Paquete / applicationId: `com.launcherfacil.app`, nombre "Launcher Fácil".
- `MainActivity.kt`: pantalla verde con "Hola 👋" (solo para comprobar el entorno).
- Versiones: AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1, Compose BOM 2024.12.01 (en `gradle/libs.versions.toml`).
- Icono provisional: vector de una casa (`res/drawable/ic_launcher.xml`).
- Git: repositorio inicializado; primer commit hecho el 2026-10-02 tras verificar el paso 1.
- El manifiesto todavía usa la categoría LAUNCHER. Falta añadir HOME/DEFAULT en el paso 6.

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
  - Arrancarlo desde la terminal: `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Medium_Phone_API_37.0`
  - JDK 21 para Gradle: `%USERPROFILE%\.jdks\jbr-21.0.11`. Hay otro en `C:\Program Files\Android\openjdk\jdk-21.0.8`.
- Samsung A16: activar las opciones de desarrollador (tocar 7 veces "Número de compilación") y la depuración USB, e instalar el Samsung Android USB Driver en Windows.

## Siguiente paso al retomar

Paso 2: pantalla principal con el reloj y la batería en grande, y los botones grandes de Llamar, WhatsApp, Teclado y SOS (por ahora solo de adorno).
