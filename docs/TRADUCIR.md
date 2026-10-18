# Traducir LlamaditApp

LlamaditApp está pensada para que la persona que la usa **no tenga que leer**: casi todo son fotos, colores e iconos. Los textos que hay que traducir son los del **panel del cuidador**, algunos avisos y, muy importante, los **SMS** que envía el SOS y la petición de ubicación.

Son unos 60 textos cortos, en un solo archivo por idioma.

## Idiomas disponibles

| Idioma | Carpeta | Estado |
|---|---|---|
| Inglés (idioma de reserva) | `values/` | Completo |
| Español | `values-es/` | Completo (idioma original) |
| Catalán | `values-ca/` | Completo, pendiente de revisión por hablante nativo |
| Gallego | `values-gl/` | Completo, pendiente de revisión por hablante nativo |
| Euskera | `values-eu/` | Completo, pendiente de revisión por hablante nativo |
| Portugués | `values-pt/` | Completo, pendiente de revisión por hablante nativo |
| Francés | `values-fr/` | Completo, pendiente de revisión por hablante nativo |
| Italiano | `values-it/` | Completo, pendiente de revisión por hablante nativo |
| Alemán | `values-de/` | Completo, pendiente de revisión por hablante nativo |

Si el móvil está en un idioma que no aparece en la lista, la app se muestra en **inglés**.

En Android 13 o superior, el cuidador puede elegir el idioma de la app sin cambiar el del teléfono: *Ajustes → Aplicaciones → LlamaditApp → Idioma*.

## Cómo añadir o corregir un idioma

1. Copia `app/src/main/res/values/strings.xml` (el inglés, que lleva comentarios para traductores) a una carpeta nueva con el código del idioma: `values-nl/` para neerlandés, `values-ro/` para rumano, `values-pt-rBR/` para portugués de Brasil, etc.
2. Traduce el texto **entre las etiquetas**, sin cambiar el `name="…"`.
3. Añade el código del idioma a `app/src/main/res/xml/locales_config.xml`, para que aparezca en el selector de idioma de Android.
4. Abre un *pull request* en GitHub, o una *issue* con el archivo traducido si no usas git.

### Reglas importantes

- **No toques los marcadores** como `%1$s`, `%1$d` o `%2$d`: la app los sustituye por el enlace de Google Maps, el porcentaje de batería, etc. Puedes cambiarlos de sitio dentro de la frase, pero deben seguir estando todos.
- `%%` es un signo de porcentaje: escribe `%%` donde quieras que salga `%`.
- Escapa los **apóstrofos** y las **comillas** con una barra: `l\'appli`, `\"Pausar\"`.
- `\n` es un salto de línea.
- **Los SMS** (`sos_first`, `sos_update`, `sos_last`, `locate_reply`…) los recibe un familiar en una emergencia: que sean claros y cortos. Tienen que caber en pocos SMS.
- **`default_keywords`** son las palabras que el familiar envía por SMS para pedir la ubicación. Escríbelas **en mayúsculas y sin tildes**: la app ignora mayúsculas, tildes y signos al comparar. El cuidador las puede cambiar después en el panel.
- Mantén en inglés los términos técnicos **Device Owner** y **WhatsApp**, y el nombre **LlamaditApp**.

## Revisar una traducción existente

Las traducciones marcadas como "pendiente de revisión" se hicieron sin hablante nativo. Si hablas alguno de esos idiomas, revisar el archivo y corregir lo que suene raro ayuda muchísimo. Lo más importante son los SMS del SOS.

## Plataforma de traducción

Si el proyecto crece, la idea es usar [Weblate](https://hosted.weblate.org/), que es gratuito para proyectos de software libre y permite traducir desde el navegador sin saber git. Mientras tanto, los *pull requests* en GitHub son bienvenidos.
