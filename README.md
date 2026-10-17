# Launcher Fácil

**Un móvil Android que se usa solo con imágenes.** Launcher Fácil convierte un teléfono Android en un dispositivo sencillo para personas que no saben leer o que se pierden con un móvil normal: fotos grandes de las personas para llamarlas, un botón SOS, el reloj y la batería en grande, y nada más. Un cuidador lo configura todo desde un panel oculto protegido con PIN.

<p align="center">
  <img src="docs/img/capturas/01-inicio.jpg" alt="Pantalla principal de Launcher Fácil" width="260">
</p>

## Qué hace

| Función | Cómo la usa la persona |
|---|---|
| 📞 **Llamar** | Toca la foto de la persona. Nada más. |
| 🎥 **Videollamada de WhatsApp** | Toca la foto y la videollamada empieza sola, sin pasar por WhatsApp. |
| 🔢 **Teclado** | Números enormes para marcar a mano. |
| 🆘 **SOS** | Mantiene pulsado el botón rojo 2 segundos: llama al contacto de confianza y le envía su ubicación por SMS (y 3 actualizaciones más, cada 10 minutos). |
| 📲 **Llamadas entrantes** | Pantalla propia con la foto en grande: verde para coger, rojo para colgar. Un solo toque, sin deslizar. También para las videollamadas de WhatsApp. |
| ✉️ **Mensajes** | Lista de SMS con la foto de quien escribe; se pueden leer en grande y borrar. |
| 📵 **Llamadas perdidas** | Un número rojo avisa de que alguien ha llamado; tocar la llamada la devuelve. |
| 🕐 **Reloj y batería** | Siempre en grande en la pantalla principal. |
| 🔊 **Volumen** | Los botones solo cambian el volumen del teléfono, con un panel enorme que se entiende sin leer. El móvil siempre vibra al recibir una llamada. |
| 📍 **¿Dónde está?** | Un familiar autorizado envía un SMS con la palabra `UBICACION` y recibe la posición en Google Maps y el nivel de batería. Solo cuando se pide: no hay seguimiento continuo. |
| 🔒 **Modo quiosco** | La persona no puede salir del launcher, ni desconfigurar el móvil, ni abrir otras apps. Tras reiniciar, arranca directamente en Launcher Fácil. |

Y por debajo, sin que la persona haga nada: Wi-Fi y Bluetooth apagados (solo datos móviles), pantalla siempre en vertical, apps innecesarias ocultas, sin pantalla de bloqueo y actualizaciones del sistema de madrugada.

Más detalles en **[docs/FUNCIONES.md](docs/FUNCIONES.md)**.

## Así se ve

| Llamar | Videollamada | Llamada entrante |
|:---:|:---:|:---:|
| <img src="docs/img/capturas/02-llamar.jpg" width="220" alt="Llamar"> | <img src="docs/img/capturas/03-videollamada.jpg" width="220" alt="Videollamada"> | <img src="docs/img/capturas/05-llamada-entrante.jpg" width="220" alt="Llamada entrante"> |
| Toca la foto para llamar | Toca la foto y empieza la videollamada de WhatsApp | Verde para coger, rojo para colgar |

| SOS | Mensajes | Llamadas perdidas |
|:---:|:---:|:---:|
| <img src="docs/img/capturas/13-sos.jpg" width="220" alt="SOS"> | <img src="docs/img/capturas/07-mensajes.jpg" width="220" alt="Mensajes"> | <img src="docs/img/capturas/09-perdidas.jpg" width="220" alt="Llamadas perdidas"> |
| Mantener pulsado 2 s: llama y envía la ubicación | Los SMS, con la foto de quien escribe | Toca una para devolver la llamada |

| Teclado | Volumen | Panel del cuidador |
|:---:|:---:|:---:|
| <img src="docs/img/capturas/04-teclado.jpg" width="220" alt="Teclado"> | <img src="docs/img/capturas/12-volumen.jpg" width="220" alt="Volumen"> | <img src="docs/img/capturas/15-panel-1.jpg" width="220" alt="Panel del cuidador"> |
| Números enormes | Un panel que se entiende sin leer | Oculto y con PIN, solo para el cuidador |

Todas las pantallas, explicadas una a una, en **[docs/FUNCIONES.md](docs/FUNCIONES.md)**.

<sub>Capturas del emulador con contactos inventados; fotos de Unsplash (créditos en [FUNCIONES.md](docs/FUNCIONES.md)).</sub>

## Requisitos

- Un móvil **Android 8.0 o superior** con tarjeta SIM. Probado a fondo en un **Samsung Galaxy A16** (Android 16).
- Un **ordenador** (Windows, macOS o Linux) con las herramientas de Android (`adb`), solo para la puesta en marcha.
- El móvil debe estar **recién restablecido de fábrica** o sin ninguna cuenta (Google, Samsung…) en el momento de activar el modo quiosco. Es una exigencia de Android, no de la app.
- Opcional: **WhatsApp** (no WhatsApp Business) para las videollamadas.

## Instalación

La guía completa, paso a paso y desde un móvil de fábrica, está en **[docs/INSTALACION.md](docs/INSTALACION.md)**.

Resumen para quien ya conoce `adb`:

```bash
adb install launcher-facil.apk
adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver
adb shell appops set com.launcherfacil.app WRITE_SETTINGS allow
adb shell cmd notification allow_listener com.launcherfacil.app/com.launcherfacil.app.whatsapp.WhatsAppCallListener
```

Después: mantener pulsado el **reloj 5 segundos** → crear el PIN del cuidador → configurar contactos, SOS y WhatsApp → repasar la [configuración recomendada](docs/CONFIGURACION.md).

## Documentación

- [Funciones en detalle](docs/FUNCIONES.md): qué ve la persona y qué puede ajustar el cuidador.
- [Instalación paso a paso desde cero](docs/INSTALACION.md).
- [Configuración recomendada de Android](docs/CONFIGURACION.md): ajustes del teléfono, la SIM, WhatsApp y los móviles de los familiares para que todo funcione bien.
- [Solución de problemas](docs/PROBLEMAS.md).
- [Compilar el proyecto](docs/DESARROLLO.md): para desarrolladores.

## Privacidad

Launcher Fácil **no tiene servidor ni envía datos a ningún sitio**. La ubicación solo sale del móvil por SMS, cuando la pide un contacto autorizado o cuando se pulsa SOS, y va directamente al teléfono de ese familiar. No hay cuentas, ni publicidad, ni analítica.

## Tecnología

Kotlin + Jetpack Compose. Android 8.0+ (minSdk 26, targetSdk 35). La app es *Device Owner* (modo quiosco con Lock Task), la app de Teléfono predeterminada (`InCallService`) y lee las notificaciones de WhatsApp para mostrar sus llamadas con la pantalla propia.
