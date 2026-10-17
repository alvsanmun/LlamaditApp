# Solución de problemas

### No puedo activar la depuración USB / "Bloqueado por Bloqueador automático"
En Samsung, desactiva antes el **Bloqueador automático**: *Ajustes → Seguridad y privacidad → Bloqueador automático*.

### `adb devices` no muestra el móvil, o sale `unauthorized`
- Prueba otro cable: muchos cables solo cargan y no transmiten datos.
- Desbloquea el móvil y acepta el aviso **"¿Permitir depuración USB?"**.
- En Windows, si sigue sin aparecer, instala el controlador USB del fabricante (Samsung: "Samsung USB Driver").

### `dpm set-device-owner` dice "there are already some accounts on the device"
Queda alguna cuenta en el móvil. Míralas con `adb shell dumpsys account` y quítalas en *Ajustes → Cuentas y copia de seguridad → Administrar cuentas*. En Samsung suele ser la cuenta **"Meet"** de Google Meet: `adb shell pm uninstall --user 0 com.google.android.apps.tachyon`. Si WhatsApp ya estaba instalado, también cuenta su cuenta: quítala, desactiva WhatsApp con `adb shell pm disable-user --user 0 com.whatsapp`, nombra el Device Owner y vuelve a activarlo con `adb shell pm enable --user 0 com.whatsapp` (no se pierden los chats).

### `dpm set-device-owner` dice "already several users" o "already provisioned"
El móvil tiene otro usuario o un perfil de trabajo (o Carpeta segura). Elimínalos o restablece el móvil de fábrica.

### He olvidado el PIN del cuidador
En la versión final no hay forma de recuperarlo: hay que **restablecer el móvil de fábrica** (con los botones físicos, en el modo de recuperación) y volver a instalar. En la versión de desarrollo existe una salida de emergencia por `adb` (ver [DESARROLLO.md](DESARROLLO.md)).

### Una llamada entrante no muestra la pantalla de Launcher Fácil
Launcher Fácil debe ser la **app de Teléfono predeterminada**. Compruébalo con `adb shell cmd role get-role-holders android.app.role.DIALER`: debe salir `com.launcherfacil.app`. En Android 14 o superior lo hace solo al activar el quiosco; en versiones anteriores, elígelo a mano en *Ajustes → Aplicaciones → Aplicaciones predeterminadas → App de teléfono*.

### Las videollamadas entrantes de WhatsApp no salen con la pantalla grande
Falta el **acceso a notificaciones**. En el panel del cuidador, sección "Llamadas de WhatsApp", toca *Activar acceso a notificaciones*, o ejecuta el comando del paso 5 de la [guía de instalación](INSTALACION.md).

### Un contacto no aparece en el botón de videollamada
- Debe ser **favorito ★** en Contactos.
- Debe estar marcado en **"Tienen WhatsApp (videollamada)"** en el panel del cuidador.
- Su número debe estar completo; los números españoles de 9 cifras se completan solos con el prefijo +34. Para otros países, guárdalo con el prefijo internacional (`+33…`).

### Al tocar un contacto de videollamada WhatsApp se queda en un chat o da error
Comprueba que esa persona usa WhatsApp con ese mismo número, y que WhatsApp tiene la sesión iniciada en el móvil.

### El SOS no envía la ubicación, o responde "No se ha podido obtener la ubicación"
- La **ubicación** del móvil debe estar activada (*Ajustes → Ubicación*).
- Dentro de un edificio el GPS puede tardar o fallar; entonces se usa la ubicación aproximada por red.
- Comprueba que la SIM tiene saldo o tarifa con SMS.

### Un familiar envía `UBICACION` y no recibe respuesta
- Su número debe estar en los contactos **favoritos ★** y marcado en **"Pueden pedir la ubicación por SMS"**.
- El SMS debe contener solo la palabra clave (da igual mayúsculas o tildes).
- Si el familiar escribe desde iMessage o RCS ("chat"), el mensaje no llega como SMS. Debe enviarse como **SMS normal**.

### La pantalla gira en las videollamadas
Falta el permiso para fijar la rotación: `adb shell appops set com.launcherfacil.app WRITE_SETTINGS allow`, y reinicia el móvil.

### Necesito instalar o configurar algo en el móvil
Panel del cuidador → **"Pausar el modo quiosco"**. Al terminar, **"Activar el modo quiosco"**. No uses "Quitar el Device Owner": para deshacerlo hace falta un ordenador y quitar todas las cuentas del móvil.

### `adb install` falla con `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
La versión instalada está firmada con otra clave. Como Launcher Fácil es Device Owner, no se puede desinstalar directamente: quita antes el Device Owner desde el panel del cuidador, desinstala y vuelve a instalar desde el paso 4 de la guía.
