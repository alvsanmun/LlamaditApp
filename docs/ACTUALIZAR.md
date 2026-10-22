# Actualizar LlamaditApp a la última versión

Si el móvil ya tiene LlamaditApp instalada (por ejemplo, la versión 1.1) y quieres pasar a la última, **no hace falta restablecerlo de fábrica**. Se actualiza en unos 10 minutos con el mismo instalador de Windows, y **se conserva todo**:

- los contactos, sus fotos y su orden;
- el PIN del cuidador y toda la configuración del panel (contacto del SOS, quién puede pedir la ubicación, palabras clave…);
- WhatsApp y sus videollamadas;
- el modo quiosco: LlamaditApp sigue controlando el móvil.

Se puede saltar versiones: de la 1.1 se pasa directamente a la última.

> ⚠️ **No hagas nada de esto para actualizar:** no restablezcas el móvil, no pulses **Quitar el Device Owner** en el panel y no desinstales la app. Perderías la configuración y tendrías que instalarlo todo desde cero.

**Lo que necesitas:**

- Un ordenador con **Windows** e internet.
- El **cable USB** del móvil.
- El móvil con **más de la mitad de batería**.
- El **PIN del cuidador**.

---

## Índice

1. [Mira qué versión tienes y pausa el modo quiosco](#1-mira-qué-versión-tienes-y-pausa-el-modo-quiosco)
2. [Comprueba que la "Depuración USB" sigue activada](#2-comprueba-que-la-depuración-usb-sigue-activada)
3. [Descarga el instalador nuevo](#3-descarga-el-instalador-nuevo)
4. [Conecta el móvil y ejecuta el instalador](#4-conecta-el-móvil-y-ejecuta-el-instalador)
5. [Comprueba el móvil y activa otra vez el modo quiosco](#5-comprueba-el-móvil-y-activa-otra-vez-el-modo-quiosco)
6. [Configura las novedades](#6-configura-las-novedades)
7. [Si algo sale mal](#7-si-algo-sale-mal)

---

## 1. Mira qué versión tienes y pausa el modo quiosco

1. En el móvil, **mantén pulsado el reloj 5 segundos** y escribe el **PIN** del cuidador.
2. Baja hasta el final del panel. En **Acerca de** pone la versión instalada (en la captura, **LlamaditApp 1.1**). La última versión está siempre en la [página de descargas](https://github.com/alvsanmun/LlamaditApp/releases/latest).
3. Un poco más arriba, en **Modo quiosco**, pulsa **Pausar el modo quiosco**. Así el móvil puede mostrar los avisos que hagan falta durante la actualización. Al terminar lo volverás a activar.

<table><tr>
<td><img src="img/actualizar/a01-version-y-pausar.jpg" width="260" alt="Panel del cuidador: botón Pausar el modo quiosco y versión 1.1 en Acerca de"></td>
</tr><tr>
<td align="center">① <b>Acerca de</b>: la versión.<br>② <b>Pausar el modo quiosco</b>.<br>⛔ El botón rojo, <b>no</b>.</td>
</tr></table>

---

## 2. Comprueba que la "Depuración USB" sigue activada

Es la opción que dejaste activada al instalar LlamaditApp. Normalmente sigue así, pero conviene comprobarlo:

1. En el panel, pulsa **Abrir Ajustes de Android**.
2. Ve al final de Ajustes → **Opciones de desarrollador** y comprueba que **Depuración por USB** está **activada**.
3. **Solo en Samsung:** en **Ajustes → Seguridad y privacidad**, el **Bloqueador automático** tiene que estar **desactivado**; si no, el móvil no deja instalar desde el ordenador.

<table><tr>
<td><img src="img/guia/s11-opciones-desarrollador.jpg" width="230" alt="Opciones de desarrollador al final de Ajustes"></td>
<td><img src="img/guia/s12-depuracion-usb.jpg" width="230" alt="Depuración por USB activada"></td>
</tr><tr>
<td align="center">① <b>Opciones de desarrollador</b></td><td align="center">② <b>Depuración por USB</b> activada</td>
</tr></table>

Si no aparecen las **Opciones de desarrollador** (puede pasar tras una actualización grande de Android), actívalas otra vez como en el [paso 5 de la guía de instalación](GUIA-INSTALACION.md#5-activar-la-depuración-usb).

Pulsa el botón de **inicio** del móvil para volver a LlamaditApp.

---

## 3. Descarga el instalador nuevo

Aunque ya tengas el instalador de otra vez, descárgalo de nuevo: el actual explica mejor la actualización.

1. En el ordenador, abre la [página de la última versión](https://github.com/alvsanmun/LlamaditApp/releases/latest) y pulsa **Instalador-LlamaditApp-Windows.zip**.
2. En la carpeta **Descargas**, haz clic con el botón derecho en el archivo → **Extraer todo…** → **Extraer**.
3. Se abre la carpeta **LlamaditApp-Instalador-Windows**.

<table><tr>
<td><img src="img/guia/p01-descargar-instalador.jpg" width="420" alt="Descargar el instalador desde GitHub"></td>
<td><img src="img/guia/p03-extraer-todo.jpg" width="300" alt="Extraer todo"></td>
</tr><tr>
<td align="center">① Descargar el instalador</td><td align="center">② Extraer todo</td>
</tr></table>

---

## 4. Conecta el móvil y ejecuta el instalador

1. Conecta el móvil al ordenador con el cable USB. Si el móvil pregunta qué hacer con el USB, elige **Transferir archivos**.
2. En la carpeta del instalador, haz **doble clic** en **Instalar LlamaditApp.bat**. Si Windows avisa con "Windows protegió su PC", pulsa **Más información → Ejecutar de todas formas**.
3. Lee la primera pantalla y pulsa **Intro**.

<p align="center"><img src="img/actualizar/a02-instalador-inicio.jpg" width="700" alt="Primera pantalla del instalador"></p>

4. Si en el móvil aparece **"¿Permitir depuración USB?"**, marca **Permitir siempre desde este ordenador** y pulsa **Permitir**.
5. El instalador descarga la última versión, **detecta que LlamaditApp ya está instalada** y solo la actualiza. Al final dice de qué versión a cuál ha pasado:

<p align="center"><img src="img/actualizar/a03-instalador-actualizado.jpg" width="700" alt="El instalador ha actualizado de la versión 1.1 a la 1.3"></p>

Fíjate en las líneas **"ya controla este móvil: solo se actualizará"** y **"App actualizada: de la versión 1.1 a la 1.3"**. Pulsa **Intro** para cerrar la ventana y desconecta el cable.

---

## 5. Comprueba el móvil y activa otra vez el modo quiosco

1. LlamaditApp se reinicia sola con la versión nueva. Si aparece el aviso **"Visualización en pantalla completa"**, pulsa **Entendido** (es un aviso de Android que sale una sola vez).

<p align="center"><img src="img/actualizar/a04-aviso-pantalla-completa.jpg" width="300" alt="Aviso de Android: Visualización en pantalla completa"></p>

2. Entra en el panel (reloj 5 segundos y **el mismo PIN de siempre**). Comprueba que siguen los contactos, el contacto del SOS y lo demás.
3. Baja hasta **Modo quiosco** y pulsa **Activar el modo quiosco**. En **Acerca de** ya pone la versión nueva.

<table><tr>
<td><img src="img/actualizar/a05-configuracion-conservada.jpg" width="230" alt="El contacto del SOS y el resto de la configuración se conservan"></td>
<td><img src="img/actualizar/a06-activar-quiosco.jpg" width="230" alt="Activar el modo quiosco y versión 1.3"></td>
</tr><tr>
<td align="center">① La configuración sigue igual</td><td align="center">② <b>Activar el modo quiosco</b>.<br>Versión <b>1.3</b> en Acerca de.</td>
</tr></table>

✅ **Listo.** La pantalla principal se queda igual que antes; las funciones nuevas vienen **desactivadas** hasta que el cuidador las active.

---

## 6. Configura las novedades

Lo nuevo de las últimas versiones, todo desde el panel del cuidador:

| Novedad | Dónde se explica |
|---|---|
| **WhatsApp completo** (1.4): un botón aparte para usar los chats, audios, fotos y vídeos, con una pestaña con una casa para volver | [Guía, paso 9.4](GUIA-INSTALACION.md#94-whatsapp-completo-opcional) |
| **Botón para volver** (1.4): la casa grande de arriba o una flecha abajo a la izquierda | [Guía, paso 9.1](GUIA-INSTALACION.md#91-adaptar-la-pantalla-principal) |
| **Pantalla principal a medida** (1.3): mover, agrandar u ocultar botones, y otro dibujo para el SOS (ambulancia, cruz, corazón…) | [Guía, paso 9.1](GUIA-INSTALACION.md#91-adaptar-la-pantalla-principal) |
| **Volver a casa** (1.3): dirección en grande, ruta a pie con voz y foto del portal al llegar | [Guía, paso 9.2](GUIA-INSTALACION.md#92-volver-a-casa-opcional) |
| **Wi-Fi opcional** (1.3) | [Guía, paso 9.3](GUIA-INSTALACION.md#93-wi-fi-opcional) |
| **Contactos desde una cuenta de Google** (1.3) | [Guía, paso 8, opción B](GUIA-INSTALACION.md#opción-b-desde-una-cuenta-de-google) |
| **Avisos por SMS de "Volver a casa"** y aviso del coste de los SMS (1.3) | [Funciones: Volver a casa](FUNCIONES.md#11-volver-a-casa) |

Y algunos [consejos](FUNCIONES.md#consejos), como descargar el mapa de la ciudad en Google Maps para que la ruta a casa funcione sin datos.

La lista completa de cambios de cada versión está en la [página de versiones](https://github.com/alvsanmun/LlamaditApp/releases).

---

## 7. Si algo sale mal

| El instalador dice… | Qué hacer |
|---|---|
| "No encuentro el móvil" | Comprueba el cable (mejor el original) y que la **Depuración USB** está activada (paso 2). Prueba otro puerto USB del ordenador. |
| Se queda esperando y el móvil pregunta **"¿Permitir depuración USB?"** | Pulsa **Permitir** en el móvil. Si no aparece la pregunta, comprueba que pausaste el modo quiosco (paso 1). |
| "Ya hay otra versión instalada con una firma distinta" (`UPDATE_INCOMPATIBLE`) | La app del móvil no salió de la [página oficial](https://github.com/alvsanmun/LlamaditApp/releases) (por ejemplo, la compiló alguien por su cuenta). No se puede actualizar encima: habría que quitar el Device Owner, desinstalarla e instalar de nuevo, y se perdería la configuración del panel. |
| "El móvil ya tiene una versión más nueva" | No hay nada que actualizar. |
| La instalación falla en un Samsung | Desactiva el **Bloqueador automático** (paso 2) y vuelve a abrir el instalador. |
| "Windows protegió su PC" | **Más información → Ejecutar de todas formas**. |

**Para quien ya usa `adb`:** basta con descargar el APK de la última versión y ejecutar `adb install -r LlamaditApp-1.3.apk`. El `-r` reinstala conservando los datos.

¿Otro problema? Mira [Problemas frecuentes](PROBLEMAS.md) o abre una [incidencia en GitHub](https://github.com/alvsanmun/LlamaditApp/issues).

---

<sub>Capturas del emulador de Android actualizando de la versión 1.1 a la 1.3, con contactos inventados; las de los menús de Ajustes, de un Samsung Galaxy A16.</sub>
