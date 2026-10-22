# Configuración recomendada de Android

LlamaditApp aplica él solo la mayoría de los ajustes en cuanto se activa el modo quiosco: Wi-Fi y Bluetooth apagados, pantalla en vertical, apps ocultas, sin pantalla de bloqueo y actualizaciones de madrugada. Pero hay ajustes del teléfono, de la SIM y de algunas apps que **una app no puede cambiar** y que conviene dejar bien hechos para que todo funcione sin sorpresas.

Los menús son los de un **Samsung Galaxy A16** (One UI 8). En otras marcas los nombres son parecidos.

> **¿Cómo llego a los Ajustes con el quiosco activo?** Panel del cuidador (mantener pulsado el reloj 5 s y PIN) → **"Abrir Ajustes de Android"**. Al volver a LlamaditApp, el quiosco se activa solo. Para tareas largas, usa **"Pausar el modo quiosco"** y luego **"Activar el modo quiosco"**.

Cada ajuste está marcado como:
- **Imprescindible**: sin él, algo no funcionará.
- **Recomendado**: mejora la fiabilidad, la batería o evita sustos.

---

## 1. Tarjeta SIM y operador

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Quitar el PIN de la SIM** · Imprescindible | *Ajustes → Seguridad y privacidad → Más ajustes de seguridad → Seguridad de tarjeta SIM → desactivar "Bloquear tarjeta SIM"* | Si el móvil se reinicia (por una actualización o porque se quede sin batería), pediría el PIN y se quedaría sin llamadas ni SOS hasta que alguien lo escribiera. |
| **Tarifa con llamadas, SMS y datos** · Imprescindible | Con el operador | El SOS y la localización van por **SMS**. Los datos móviles hacen falta para WhatsApp y las actualizaciones, porque el Wi-Fi está apagado salvo que el cuidador lo active en el panel. |
| **Desactivar el buzón de voz** · Recomendado | Llamando al operador (o con el código que te indique) | Si salta el buzón, quien llama cree que ha hablado con la persona, que nunca lo escuchará. Sin buzón, la llamada queda como **perdida** y sale el número rojo en LlamaditApp. |
| **Avisos de "te han llamado"** · Recomendado | Con el operador | Si el móvil estuvo sin cobertura o apagado, el operador avisa por SMS de las llamadas recibidas. |

## 2. Seguridad y pantalla de bloqueo

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Sin bloqueo de pantalla** · Imprescindible | *Ajustes → Pantalla de bloqueo y AOD → Tipo de bloqueo de pantalla → Ninguno* | LlamaditApp quita la pantalla de bloqueo, pero solo puede hacerlo si no hay PIN, patrón, contraseña ni huella. |
| **Bloqueador automático desactivado** (solo Samsung) · Imprescindible durante la instalación | *Ajustes → Seguridad y privacidad → Bloqueador automático* | Mientras está activo no deja instalar desde el ordenador. Al terminar puedes volver a activarlo, pero tendrás que desactivarlo para cada actualización de LlamaditApp. |
| **SOS de emergencia del sistema** · Recomendado: revisarlo | *Ajustes → Seguridad y emergencias → SOS de emergencia* | Samsung llama al 112 si se pulsa el botón lateral 5 veces seguidas. Una persona que juega con el botón podría activarlo sin querer. Decide si lo dejas activado o lo quitas; el SOS de LlamaditApp es independiente. |

## 3. Batería

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Que WhatsApp nunca se suspenda** · Recomendado | *Ajustes → Batería → Límites de uso en segundo plano → Aplicaciones sin autoinactivación → + → WhatsApp* (en versiones anteriores de One UI: "Aplicaciones que nunca se suspenden") | Samsung "duerme" las apps para ahorrar batería. Si duerme WhatsApp, las videollamadas pueden llegar tarde. LlamaditApp no aparece en la lista porque, como administradora del móvil, Android ya la exime. |
| **Sin ahorro de energía extremo** · Recomendado | *Ajustes → Batería → Ahorro de energía → Desactivado* | El modo de ahorro limita los datos en segundo plano y puede retrasar WhatsApp y la ubicación. |
| **Desactivar "Poner en suspensión las apps sin usar"** · Recomendado | *Ajustes → Batería → Límites de uso en segundo plano* | Evita que el sistema retire permisos o duerma apps que se usan poco, como WhatsApp si apenas hay videollamadas. |

> La batería dura mucho más que en un móvil normal: sin Wi-Fi, sin Bluetooth y con las apps innecesarias ocultas. Aun así, acostumbra a la persona a cargarlo **cada noche** (una base de carga ayuda mucho).

## 4. Sonido y vibración

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Volumen del tono de llamada alto** · Recomendado | Botones de volumen, en la pantalla principal | Lo controla LlamaditApp. Ponlo alto: la persona puede bajarlo, pero nunca quedará mudo (como mínimo, vibra). |
| **"No molestar" desactivado** · Imprescindible | *Ajustes → Notificaciones → No molestar → Desactivado, y sin horarios programados* | Con "No molestar" las llamadas no sonarían. Revisa que no tenga un horario nocturno programado. |
| **Un tono de llamada claro** · Recomendado | *Ajustes → Sonidos y vibración → Tono de llamada* | Elige uno fuerte y reconocible. LlamaditApp usa el tono del sistema. |

## 5. Pantalla

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Tiempo de espera de la pantalla: 1 o 2 minutos** · Recomendado | *Ajustes → Pantalla → Tiempo de espera de la pantalla* | Un tiempo muy corto apaga la pantalla mientras la persona aún está mirando. Uno muy largo gasta batería. |
| **Brillo automático** · Recomendado | *Ajustes → Pantalla → Brillo adaptable* | Se ve bien en la calle sin gastar de más en casa. |
| **Pulsar dos veces para encender** · Recomendado | *Ajustes → Funciones avanzadas → Movimientos y gestos → Tocar dos veces para encender* | Para encender la pantalla basta con tocarla, sin buscar el botón lateral. |

## 6. Fecha, hora e idioma

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Fecha y hora automáticas** · Imprescindible | *Ajustes → Administración general → Fecha y hora → Automática* | El reloj grande de la pantalla principal y la hora de los SMS del SOS dependen de ello. |
| **Idioma del teléfono** · Recomendado | *Ajustes → Administración general → Idioma* | La fecha de la pantalla principal sale en el idioma del teléfono. |

## 7. Ubicación

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Ubicación activada** · Imprescindible | *Ajustes → Ubicación → Activado* | Sin ella, el SOS y las peticiones de ubicación responden "No se ha podido obtener la ubicación". |
| **Precisión de ubicación de Google activada** · Recomendado | *Ajustes → Ubicación → Servicios de ubicación → Precisión de la ubicación de Google* | Usa las antenas de telefonía cuando no hay GPS, por ejemplo dentro de casa. Como el Wi-Fi está apagado, ayuda mucho en interiores. |
| **Mapa de la ciudad descargado en Google Maps** · Recomendado si se usa "Volver a casa" | Panel → *Probar la ruta* → en Maps, cerrar la navegación (**X**) → foto de perfil → *Mapas sin conexión* → *Selecciona tu propio mapa* → *Descargar* | La ruta a casa funciona aunque no haya datos (metro, sótanos, zonas sin cobertura). El mapa ocupa bastante: descárgalo con el Wi-Fi encendido un momento desde el panel. Google Maps lo actualiza solo. |
| **Aceptar las pantallas de bienvenida de Google Maps** · Imprescindible si se usa "Volver a casa" | Panel → *Probar la ruta* (una vez) | Si no, la primera vez que la persona pulse la flecha se encontraría con las condiciones de la navegación de Google Maps. |

## 8. Mensajes (SMS)

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Desactivar las "Funciones de chat" (RCS)** · Recomendado | *App Mensajes de Google → foto de perfil → Ajustes de Mensajes → Funciones de chat (o RCS) → Desactivar* | Con RCS, los mensajes de otros móviles Android pueden llegar como "chat" por internet en lugar de SMS. LlamaditApp solo lee **SMS**, así que una petición de `UBICACION` enviada como chat no se respondería. |
| **Mensajes de Google como app de SMS** · Recomendado | Viene así de serie | LlamaditApp muestra los SMS, pero el sistema necesita una app de SMS predeterminada. No la cambies. |

## 9. Cuenta de Google y Play Store

Si se usa WhatsApp o si los contactos se gestionan desde una cuenta de Google (el modo quiosco debe activarse **antes** de añadir la cuenta; ver la [guía de instalación](INSTALACION.md)). Se añade desde el panel del cuidador → *Contactos desde una cuenta de Google* → **Añadir una cuenta de Google**.

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Actualizar apps "Usando cualquier red"** · Imprescindible si se usa WhatsApp | *Play Store → foto de perfil → Ajustes → Preferencias de red → Actualizar apps automáticamente* | Sin Wi-Fi, WhatsApp no se actualizaría nunca, y las versiones antiguas dejan de funcionar al cabo de unos meses. |
| **Descargar actualizaciones del sistema por datos móviles** (solo Samsung) · Recomendado | Desde el ordenador: `adb shell settings put global SOFTWARE_UPDATE_WIFI_ONLY2 0` | Samsung solo descarga las actualizaciones por Wi-Fi, que estará apagado. Una app no puede cambiar este ajuste. |
| **Una cuenta de Google solo para este móvil** · Recomendado | En [accounts.google.com](https://accounts.google.com), desde el ordenador | Así los contactos se gestionan desde [contacts.google.com](https://contacts.google.com) sin mezclarse con los del cuidador: en el móvil aparecen los favoritos (★) de todas sus cuentas. |
| **Sincronización automática de contactos** · Imprescindible si se usan contactos de Google | Panel del cuidador → *Contactos desde una cuenta de Google* | Sin ella, los contactos nuevos o cambiados en contacts.google.com no llegan solos al móvil. El panel avisa si está apagada. |
| **Copia de seguridad de Google** · Recomendado | *Ajustes → Cuentas y copia de seguridad → Copia de seguridad de Google Drive* | Guarda los contactos con sus fotos. Si el móvil se pierde o se rompe, se recuperan en el nuevo. |

## 10. WhatsApp

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Notificaciones de llamadas activadas** · Imprescindible | *WhatsApp → Ajustes → Notificaciones → Llamadas*, y en *Ajustes de Android → Notificaciones → WhatsApp* | LlamaditApp detecta las videollamadas entrantes **por su notificación**. Si están silenciadas, no aparecerá la pantalla grande. |
| **Acceso a notificaciones para LlamaditApp** · Imprescindible | Panel del cuidador → "Activar acceso a notificaciones" | Lo mismo: sin este permiso, LlamaditApp no ve las llamadas de WhatsApp. |
| **No descargar fotos ni vídeos automáticamente** · Recomendado | *WhatsApp → Ajustes → Almacenamiento y datos → Descarga automática → nada en "Datos móviles"* | La persona no usa los chats; así no gasta datos ni almacenamiento. |
| **Silenciar los grupos** · Recomendado | En cada grupo → *Silenciar notificaciones → Siempre* | Evita que el móvil suene a todas horas con mensajes que la persona no va a leer. |
| **Foto de perfil del cuidador** · Recomendado | En el WhatsApp de cada familiar | Si un contacto no tiene foto en la agenda, en las videollamadas entrantes se usa su foto de WhatsApp. |

## 11. Contactos

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Una foto clara de la cara de cada persona** · Imprescindible | *Contactos → contacto → Editar → icono de la cámara* | Es lo único que ve la persona para saber a quién llama. Mejor de cerca, con buena luz y fondo liso. |
| **Marcar como favorito ★** · Imprescindible | *Contactos → contacto → estrella*, o la estrella en [contacts.google.com](https://contacts.google.com) | Solo los favoritos aparecen en LlamaditApp. |
| **Números con prefijo internacional** · Recomendado para números extranjeros | Al guardar el contacto: `+33 …`, `+44 …` | Los números españoles de 9 cifras se completan solos con +34. Los de otros países necesitan el prefijo para la videollamada de WhatsApp. |
| **Pocos favoritos** · Recomendado | — | Con 4 a 6 caras la pantalla se entiende de un vistazo. Pon arriba a quien más llama (panel del cuidador → Orden de los contactos). |

## 12. En los móviles de los familiares

| Ajuste | Dónde | Por qué |
|---|---|---|
| **Que las llamadas y SMS de este teléfono suenen siempre** · Muy recomendado | **iPhone**: Contactos → este número → Editar → Tono → **"Saltar silencio"** (y lo mismo en *Tono de mensaje*). **Android**: marcarlo como favorito y permitirlo como excepción en "No molestar" | El SOS tiene que oírse aunque el familiar tenga el móvil en silencio o sea de noche. |
| **Un tono distinto para este contacto** · Recomendado | Al editar el contacto | Para reconocer al momento que llama o escribe la persona. |
| **Enviar las peticiones como SMS** · Imprescindible | En el móvil del familiar | La palabra `UBICACION` debe llegar como **SMS**, no por WhatsApp. Si en el teléfono de la persona están desactivadas las funciones de chat (RCS, apartado 8), los mensajes de iPhone y Android le llegan siempre como SMS. |

---

## Lista rápida

Antes de entregar el móvil, comprueba:

- [ ] SIM sin PIN.
- [ ] Sin bloqueo de pantalla.
- [ ] "No molestar" desactivado y sin horarios.
- [ ] Ubicación activada.
- [ ] Fecha y hora automáticas.
- [ ] WhatsApp en "Aplicaciones sin autoinactivación".
- [ ] Funciones de chat (RCS) desactivadas en Mensajes.
- [ ] Play Store actualiza "usando cualquier red".
- [ ] Notificaciones de llamadas de WhatsApp activadas, y acceso a notificaciones para LlamaditApp.
- [ ] Contactos favoritos con foto, en el orden adecuado.
- [ ] Si se usa "Volver a casa": casa marcada, foto del portal, ruta probada una vez y mapa de la ciudad descargado.
- [ ] La tarifa incluye SMS (SOS, ubicación y avisos de "Volver a casa").
- [ ] Los familiares tienen el número guardado con "Saltar silencio".
- [ ] El modo quiosco está **activado**.
