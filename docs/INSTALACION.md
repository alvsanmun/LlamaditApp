# Instalación y configuración desde cero (guía técnica)

> 👉 ¿No sabes de informática? Sigue mejor la **[guía paso a paso con capturas](GUIA-INSTALACION.md)**, que usa el instalador de un clic para Windows. Esta es la versión técnica, con los comandos `adb`.

Esta guía deja un móvil Android listo para entregar, empezando desde un teléfono recién sacado de la caja o restablecido de fábrica. Se tarda alrededor de **una hora** la primera vez.

**Resumen de los pasos:** 1) preparar el móvil · 2) depuración USB · 3) preparar el ordenador · 4) instalar · 5) activar el modo quiosco · 6) PIN del cuidador · 7) contactos · 8) Google y WhatsApp · 9) panel del cuidador · 10) ajustes de Android · 11) comprobar · 12) entregar · 13) móviles de los familiares.

Los pasos y nombres de menús son los de un **Samsung Galaxy A16** (Android 16, One UI 8). En otras marcas los menús se llaman parecido; donde hay diferencias importantes se indica.

> **¿Por qué hace falta un ordenador?** Para que la persona no pueda salir de LlamaditApp, la app tiene que ser el *administrador del dispositivo* (*Device Owner*). Android solo permite nombrarlo desde un ordenador y con el móvil sin cuentas. Es una medida de seguridad de Android, y solo hay que hacerlo una vez.

---

## Qué necesitas

- El **móvil Android** (8.0 o superior) con su **tarjeta SIM**.
- Un **ordenador** con Windows, macOS o Linux y un **cable USB** que transmita datos.
- El archivo **`launcher-facil.apk`** (en la sección *Releases* del repositorio, o compílalo tú siguiendo [DESARROLLO.md](DESARROLLO.md)).
- El **PIN de la SIM**, para quitarlo.
- Una **cuenta de Google** para el teléfono (si se va a usar WhatsApp) y un **número para registrar WhatsApp** (normalmente el de la SIM).
- Las **fotos** de las personas a las que va a llamar (se pueden hacer con el propio móvil).

---

## Paso 1. Preparar el móvil

1. Si el móvil ya se ha usado, **restablécelo de fábrica**: *Ajustes → Administración general → Restablecer → Restablecer todos los datos*.
2. Inserta la **SIM** y enciende el teléfono.
3. En el asistente de bienvenida:
   - Elige el idioma.
   - **No añadas ninguna cuenta de Google** ("Omitir"). Se añadirá más tarde.
   - **No añadas cuenta de Samsung** ("Omitir").
   - **No pongas PIN, patrón ni huella** ("Omitir" / "Ninguno"). LlamaditApp quita la pantalla de bloqueo, y eso solo funciona si el móvil no tiene ninguna.
   - Puedes conectarte a un Wi-Fi durante el asistente; luego LlamaditApp lo apagará.
4. **Quita el PIN de la SIM**, para que tras un reinicio el teléfono funcione sin que nadie tenga que hacer nada: *Ajustes → Seguridad y privacidad → Más ajustes de seguridad → Seguridad de tarjeta SIM → desactivar "Bloquear tarjeta SIM"* (pide el PIN actual).

## Paso 2. Activar la depuración USB

1. **Solo en Samsung**: desactiva el **Bloqueador automático** (*Ajustes → Seguridad y privacidad → Bloqueador automático → Desactivado*). Mientras esté activo, no deja activar la depuración USB ni instalar apps desde el ordenador.
2. Activa las **opciones de desarrollador**: *Ajustes → Acerca del teléfono → Información de software* y toca **"Número de compilación" 7 veces**.
3. Entra en *Ajustes → Opciones de desarrollador* y activa **"Depuración USB"**.

## Paso 3. Preparar el ordenador

1. Descarga las **Android SDK Platform-Tools** desde la web oficial de Android: <https://developer.android.com/tools/releases/platform-tools> y descomprímelas (por ejemplo, en `C:\platform-tools`).
2. Abre una terminal en esa carpeta (en Windows: escribe `cmd` en la barra de direcciones del explorador, estando dentro de la carpeta).
3. Conecta el móvil por USB. En el móvil aparece **"¿Permitir depuración USB?"**: marca *Permitir siempre desde este ordenador* y acepta.
4. Comprueba que el ordenador ve el móvil:

   ```bash
   adb devices
   ```

   Debe aparecer una línea con un número de serie y la palabra `device`. Si pone `unauthorized`, acepta el aviso en el móvil.

> En Windows, si la terminal no encuentra `adb`, escribe `.\adb` en lugar de `adb` en todos los comandos.

## Paso 4. Instalar LlamaditApp

Copia `launcher-facil.apk` a la carpeta de platform-tools y ejecuta:

```bash
adb install launcher-facil.apk
```

Debe responder `Success`.

## Paso 5. Activar el modo quiosco (Device Owner)

1. Comprueba que el móvil **no tiene ninguna cuenta**:

   ```bash
   adb shell dumpsys account | grep -i "Account {"
   ```

   (En Windows, usa `findstr` en lugar de `grep`.) Si no sale nada, perfecto. Si sale alguna cuenta, quítala en *Ajustes → Cuentas y copia de seguridad → Administrar cuentas*.

   > **Samsung:** la app Google Meet crea sola una cuenta "Meet" que impide el paso siguiente. Si aparece, desinstálala (se puede recuperar más tarde):
   >
   > ```bash
   > adb shell pm uninstall --user 0 com.google.android.apps.tachyon
   > ```

2. Nombra a LlamaditApp administrador del dispositivo:

   ```bash
   adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver
   ```

   Debe responder `Success: Device owner set to package ...`. **En unos segundos LlamaditApp se abre y queda bloqueado**: ya no se puede salir de él. En ese momento también se apagan el Wi-Fi y el Bluetooth, se ocultan las apps innecesarias y LlamaditApp pasa a ser la app de Teléfono.

   Si responde `Not allowed to set the device owner because there are already some accounts on the device`, vuelve al punto 1: queda alguna cuenta.

3. Permite que fije la **pantalla siempre en vertical**:

   ```bash
   adb shell appops set com.launcherfacil.app WRITE_SETTINGS allow
   ```

4. Permite que muestre las **videollamadas entrantes de WhatsApp** con su propia pantalla (también se puede hacer luego desde el panel del cuidador):

   ```bash
   adb shell cmd notification allow_listener com.launcherfacil.app/com.launcherfacil.app.whatsapp.WhatsAppCallListener
   ```

5. **Solo Samsung**: permite que las actualizaciones del sistema se descarguen por datos móviles (el Wi-Fi estará siempre apagado):

   ```bash
   adb shell settings put global SOFTWARE_UPDATE_WIFI_ONLY2 0
   ```

6. Reinicia el móvil para comprobar que arranca directamente en LlamaditApp, sin pantalla de bloqueo:

   ```bash
   adb reboot
   ```

> **Android 13 o anterior:** LlamaditApp no puede hacerse app de Teléfono él solo. Hazlo a mano desde el panel del cuidador: *Abrir Ajustes de Android → Aplicaciones → Aplicaciones predeterminadas → App de teléfono → LlamaditApp*.

## Paso 6. Crear el PIN del cuidador

1. En la pantalla principal, **mantén pulsado el reloj 5 segundos** hasta que el móvil vibre.
2. Escribe un **PIN de 4 cifras** y repítelo.
3. Se abre el **panel del cuidador**. Para volver a entrar más adelante: reloj 5 segundos y el PIN.

> ⚠️ **Apunta el PIN en un sitio seguro.** Sin él no se puede entrar al panel, y la única salida es restablecer el móvil de fábrica.

## Paso 7. Añadir los contactos

En el panel del cuidador, toca **"Abrir Contactos (favoritos y fotos)"**. Para cada persona:

1. Crea el contacto con su **nombre y número**.
2. Ponle una **foto grande y clara de su cara** (toca el icono de la cámara al editar el contacto). Es lo que verá la persona.
3. Márcalo como **favorito ★**. **Solo los favoritos aparecen** en LlamaditApp.

Cuando termines, vuelve atrás hasta LlamaditApp: el modo quiosco vuelve a activarse solo.

## Paso 8. Cuenta de Google y WhatsApp (opcional)

Solo si va a usar videollamadas de WhatsApp.

1. En el panel del cuidador, toca **"Pausar el modo quiosco"**. El móvil se puede usar con normalidad (también tras reiniciar) hasta que lo vuelvas a activar.
2. Toca **"Abrir Ajustes de Android"** y añade la **cuenta de Google**: *Cuentas y copia de seguridad → Administrar cuentas → Añadir cuenta → Google*.
3. Abre **Play Store**. LlamaditApp sigue siendo la pantalla de inicio y no tiene lista de apps, así que se abre desde los ajustes: *Ajustes → Aplicaciones → Google Play Store → Abrir*. Instala **WhatsApp**, ábrelo y regístralo con el número de la SIM (llegará un SMS de verificación).
4. En Play Store: *foto de perfil → Ajustes → Preferencias de red → Actualizar apps automáticamente → "Usando cualquier red"*. Así WhatsApp se mantendrá actualizado con los datos móviles.
4b. **Que WhatsApp no se duerma** (imprescindible): *Ajustes → Batería → Límites de uso en segundo plano → Aplicaciones sin autoinactivación → + → WhatsApp* (otros móviles: *Ajustes → Aplicaciones → WhatsApp → Batería → Sin restricciones*). Si no, las videollamadas pueden no sonar.
5. Pulsa el botón de inicio para volver a LlamaditApp, entra en el panel del cuidador y toca **"Activar el modo quiosco"**. Al activarlo, LlamaditApp concede a WhatsApp los permisos de cámara, micrófono y contactos (dentro del quiosco no se pueden mostrar los avisos de permisos).
6. En la sección **"Tienen WhatsApp (videollamada)"**, marca las personas que usan WhatsApp. Aparecerán en el botón de videollamada.
7. Si la sección **"Llamadas de WhatsApp"** dice *Desactivado*, toca **"Activar acceso a notificaciones"** y activa LlamaditApp en la lista.

> Si en la primera videollamada WhatsApp pide algún permiso o no se ve la cámara, **reinicia el móvil** una vez.

## Paso 9. Configurar el panel del cuidador

- **Orden de los contactos**: con las flechas ↑ y ↓, pon arriba a quien más llama.
- **Contacto para el SOS**: elige a quién llama y avisa el botón SOS.
- **Pueden pedir la ubicación por SMS**: marca los familiares autorizados.
- **Palabras clave**: déjalas como están (`UBICACION, DONDE ESTAS`) o cámbialas.

## Paso 10. Ajustes recomendados de Android

Hay ajustes del teléfono que una app no puede cambiar y que conviene dejar bien hechos. Están todos explicados en **[CONFIGURACION.md](CONFIGURACION.md)**. Los más importantes:

- **"No molestar" desactivado** y sin horarios programados: si no, las llamadas no suenan.
- **Ubicación activada**, con la precisión de Google: si no, el SOS no puede enviar la posición.
- **Fecha y hora automáticas**.
- **WhatsApp en "Aplicaciones sin autoinactivación"** (*Ajustes → Batería → Límites de uso en segundo plano*). LlamaditApp no hace falta: como administradora del móvil, Android ya la exime.
- **Funciones de chat (RCS) desactivadas** en la app Mensajes: así las peticiones de ubicación llegan siempre como SMS.
- **Notificaciones de llamadas de WhatsApp activadas**.
- **Tiempo de espera de la pantalla** de 1 o 2 minutos.
- Revisar el **SOS de emergencia de Samsung** (5 pulsaciones del botón lateral llaman al 112).

Para cambiarlos: panel del cuidador → **"Abrir Ajustes de Android"**.

## Paso 11. Comprobar que todo funciona

Antes de entregar el móvil, prueba:

- [ ] Llamar a un contacto tocando su foto, y colgar.
- [ ] Recibir una llamada: sale la foto grande, vibra, se coge con el verde y se cuelga con el rojo.
- [ ] Teclado: marcar un número a mano.
- [ ] SOS (avisa antes a la persona de confianza): mantenerlo pulsado 2 s. Llama y llega el SMS con la ubicación.
- [ ] Desde el móvil de un familiar, enviar `UBICACION` por SMS: llega la respuesta con el enlace.
- [ ] Videollamada de WhatsApp saliente y entrante. Al colgar, vuelve a LlamaditApp.
- [ ] Botones de volumen: aparece el panel grande.
- [ ] Reiniciar el móvil: arranca solo en LlamaditApp.
- [ ] Intentar salir con los botones de inicio y de apps recientes: no se puede.

## Paso 12. Antes de entregarlo

- Desconecta el cable USB.
- (Opcional) Con el quiosco en pausa, desactiva la **depuración USB** y vuelve a activar el **Bloqueador automático** de Samsung. Ten en cuenta que, para instalar después una versión nueva de LlamaditApp desde el ordenador, tendrás que volver a activarlos.
- Asegúrate de que el modo quiosco está **activado** (en el panel debe verse el botón "Pausar el modo quiosco").

## Paso 13. Configurar los móviles de los familiares

Para que una llamada o un SMS del SOS **suene aunque el familiar tenga el móvil en silencio**:

- **iPhone**: guarda el número del teléfono como contacto → *Editar → Tono* → activa **"Saltar silencio"** (y lo mismo en *Tono de mensaje*).
- **Android**: guarda el número como contacto, márcalo como **favorito**, permítelo como excepción en **"No molestar"** y ponle un tono distinto para reconocerlo.

Recuérdales también cómo pedir la ubicación: un SMS con la palabra `UBICACION`.

---

## Actualizar LlamaditApp

> Guía paso a paso con capturas, con el instalador de Windows: **[ACTUALIZAR.md](ACTUALIZAR.md)**.

Con el móvil conectado al ordenador (depuración USB activa). Conviene pausar antes el modo quiosco desde el panel, por si el móvil tiene que preguntar "¿Permitir depuración USB?", y activarlo al terminar:

```bash
adb install -r LlamaditApp-1.3.apk
```

Se conservan el PIN, los ajustes, los contactos y el modo quiosco, y se pueden saltar versiones (de la 1.1 a la 1.3, por ejemplo). La versión nueva tiene que estar firmada con la misma clave que la instalada: las de la [página de versiones](https://github.com/alvsanmun/LlamaditApp/releases) lo están todas.

## Desinstalar LlamaditApp o entregar el móvil a otra persona

1. Panel del cuidador → **"Quitar el Device Owner"** → confirmar. El modo quiosco se desactiva para siempre y las apps ocultas vuelven a aparecer.
2. Desinstala LlamaditApp como cualquier otra app, o restablece el móvil de fábrica.

> Para **volver a activar** el modo quiosco después de quitar el Device Owner hay que repetir el paso 5, y para eso el móvil no puede tener cuentas (Google, WhatsApp…). Si solo quieres instalar o cambiar algo, usa **"Pausar el modo quiosco"**, que no tiene ese problema.

¿Algo no funciona? Consulta [PROBLEMAS.md](PROBLEMAS.md).
