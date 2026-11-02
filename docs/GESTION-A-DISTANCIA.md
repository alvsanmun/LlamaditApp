# Gestión a distancia: el proyecto de Firebase de tu familia

Con la gestión a distancia, la persona que cuida puede hacer desde su propio móvil Android (con la app del cuidador) o desde el ordenador (con la web del cuidador) casi todo lo que hace en el panel del cuidador: ver dónde está el móvil, hacerlo sonar, añadir personas y cambiar sus fotos, elegir el contacto del SOS, cambiar la pantalla principal, etc.

Para eso, los dos móviles necesitan un punto de encuentro en internet. LlamaditApp **no tiene servidores ni guarda datos de nadie**: cada familia usa **su propio proyecto de Firebase**, un servicio de Google que es gratuito para este uso. Los datos (nombres y teléfonos de las personas, sus fotos pequeñas, la configuración, la batería y las ubicaciones que se piden) se quedan en vuestro proyecto, con vuestra cuenta de Google. Nadie más puede verlos, tampoco el autor de LlamaditApp.

La gestión a distancia es opcional: sin ella, LlamaditApp funciona igual, y la ubicación por SMS también.

## Lo que necesitas

- Una cuenta de Google (la de cualquier persona de la familia; mejor la del cuidador principal).
- Un ordenador o un móvil con navegador. Se tarda unos 15 minutos y solo se hace una vez.
- No hace falta tarjeta de crédito: el plan gratuito (Spark) sobra para una familia.

## 1. Crear el proyecto

1. Entra en <https://console.firebase.google.com> con la cuenta de Google.
2. Pulsa **Crear un proyecto** (o **Comenzar con un proyecto de Firebase**).
3. Ponle un nombre, por ejemplo `llamaditapp-familia`. Acepta las condiciones y continúa.
4. Cuando te pregunte por **Gemini** y por **Google Analytics**, desactívalos (no hacen falta).
5. Pulsa **Crear proyecto** y espera a que termine.

## 2. Activar el acceso anónimo

Los móviles entran en el proyecto sin correo ni contraseña, con un "usuario anónimo".

1. En el menú de la izquierda: **Seguridad → Authentication** (o **Compilación → Authentication**) y pulsa **Comenzar**.
2. En la pestaña **Método de acceso**, elige **Anónimo**, actívalo y pulsa **Guardar**.
3. **Importante:** si en la configuración de Authentication ves la opción de **limpieza automática de cuentas anónimas**, déjala **desactivada**. Si se activa, a los 30 días se borrarían las vinculaciones y habría que repetirlas.

## 3. Crear la base de datos

1. En el menú: **Bases de datos y almacenamiento → Firestore Database** (o **Compilación → Firestore Database**) y pulsa **Crear base de datos**.
2. Edición **Standard**. Ubicación: **europe-southwest1 (Madrid)** (o la más cercana). La ubicación no se puede cambiar después.
3. Elige **Comenzar en modo de producción** y pulsa **Crear**.

## 4. Pegar las reglas de seguridad

Las reglas deciden quién puede leer y escribir: cada móvil solo lo ven los cuidadores que ha aceptado.

1. En Firestore Database, abre la pestaña **Reglas**.
2. Borra todo lo que hay y pega el contenido de [firebase/firestore.rules](../firebase/firestore.rules) (ábrelo, pulsa el botón de copiar el archivo y pégalo).
3. Pulsa **Publicar**.

## 5. Registrar la app web y copiar sus datos

1. Vuelve a la **Descripción general del proyecto** (el icono de la casa, arriba a la izquierda).
2. Pulsa **Agregar app** y elige **Web** (el icono `</>`).
3. Ponle un apodo, por ejemplo `Cuidador`. **No** marques Firebase Hosting. Pulsa **Registrar app**.
4. Aparece un bloque de código que empieza por `const firebaseConfig = {` y tiene `apiKey`, `projectId`, `appId`… **Cópialo entero.** (Si lo necesitas más tarde: rueda dentada → **Configuración del proyecto** → **Tus apps**.)

Estos datos no son secretos: solo dicen cuál es vuestro proyecto. La seguridad la dan las reglas del paso 4.

## 6. Abrir la app o la web del cuidador y pegar los datos

Hay dos formas de gestionar el móvil, iguales por dentro:

- **App del cuidador para Android**: instala `LlamaditApp-Cuidador.apk` (en las versiones publicadas del repositorio) en tu móvil Android.
- **Web en el ordenador**: descomprime la web del cuidador (la carpeta `cuidador/`) y, en Windows, haz doble clic en **Abrir la web del cuidador.bat** (en Mac o Linux, mira su `LEEME.txt`). No cierres la ventana negra mientras la uses.

Después:

1. La primera vez te pide los **datos del proyecto**: pega el bloque copiado y pulsa **Guardar**. Se guardan solo en ese móvil o navegador.
2. Si otro cuidador ya tiene el proyecto, en lugar del bloque puedes pegar su **invitación** (paso 8).

## 7. Vincular el móvil de la persona

1. En la web: escribe tu nombre y pulsa **Vincular un móvil → Crear el código QR**. El código vale 15 minutos.
2. En el móvil de la persona: mantén pulsado el reloj 5 segundos, escribe el PIN y entra en **Gestión a distancia**.
3. Pulsa **Hacer una foto al código QR** y fotografía el código de la web. Si no estás junto a la persona, haz una captura del código, mándala a ese móvil y usa **Elegir una foto del código QR**.
4. En unos segundos el móvil dice **Vinculado** y la web abre su ficha.

El móvil necesita datos móviles o Wi-Fi. En el panel conviene pulsar **Permitir que funcione en segundo plano** si sale el aviso, para que las peticiones lleguen aunque la pantalla esté apagada.

## 8. Más cuidadores

1. En tu app o web: **Vinculación → Invitar a otro cuidador → Compartir la invitación** (por WhatsApp, por correo…).
2. Esa persona instala la app del cuidador (o abre la web en su ordenador) y pega la invitación en **Datos del proyecto**. Así queda conectada a vuestro proyecto sin repetir los pasos 1 a 5.
3. Después vincula el móvil igual que en el paso 7, con su propio código QR.

Cada cuidador vinculado aparece en el panel del móvil, en **Gestión a distancia → Cuidadores vinculados**, y se puede quitar desde ahí.

## Preguntas

**¿Cuánto cuesta?** Nada. El plan gratuito de Firebase permite 50 000 lecturas y 20 000 escrituras al día; un móvil con varios cuidadores usa una pequeña parte. Si algún día se superara, Firebase simplemente deja de responder hasta el día siguiente (sin tarjeta no puede cobrar).

**¿Quién es responsable de los datos?** Vuestra familia: el proyecto es vuestro. Si borráis el proyecto en la consola de Firebase, se borra todo.

**¿Y si cambio de proyecto?** En el panel del móvil, **Desconectar del proyecto**, y vincularlo de nuevo con un código QR de la web conectada al proyecto nuevo.

**La web dice "No se puede conectar con el proyecto".** Revisa que el acceso anónimo está activado (paso 2) y que los datos son los de la app **web** del proyecto (paso 5).

**El móvil dice "No se ha podido conectar".** Comprueba que tiene datos móviles y que la base de datos y las reglas están creadas (pasos 3 y 4).
