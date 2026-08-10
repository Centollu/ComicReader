# ComicReader

Lector de cómics digitales para Android para archivos **`.cbz`** y **`.cbr`**.
Escanea carpetas locales (y opcionalmente recursos compartidos en red), extrae las
páginas a una caché local y permite leer página a página con gestos de zoom y
desplazamiento. El progreso de lectura se guarda localmente y se muestra en la
pestaña **Historial**.

> El idioma de la interfaz es **español**.

## Características

- **Formato CBZ / CBR** — descompresión de archivos ZIP y RAR con detección por
  *magic bytes* (no depende de la extensión).
- **Lectura a pantalla completa** con modo inmersivo:
  - Cambiar de página deslizando.
  - Zoom con pellizco (**1x–8x**).
  - Con zoom: un dedo desplaza la página; sin zoom: deslizar cambia de página.
  - Tocar las esquinas inferior izquierda/derecha para retroceder/avanzar (sin zoom).
  - Doble toque en el centro para hacer zoom (2.5x); con zoom activo, doble toque restablece.
- **Carga incremental** — la extracción continúa en segundo plano mientras lees; las
  páginas aparecen a medida que se descomprimen.
- **Progreso de lectura persistente** — cada página que abres se guarda en una base de
  datos local (Room); desde **Historial** puedes retomar donde lo dejaste.
- **Biblioteca con búsqueda y ordenación**:
  - Búsqueda por título, autor, serie, editorial y arco argumental.
  - Orden por nombre, ruta o fecha de añadido.
  - Cuadrícula con número de columnas configurable.
- **Gestión de metadatos** — título, número, serie, autores, editorial y arco
  argumental, con lectura automática de `ComicInfo.xml`.
- **Selector de portada** — pulsa un cómic para cambiar la imagen de portada usando
  cualquiera de las páginas del archivo.
- **Explorador de carpetas** — navega por los volúmenes de almacenamiento y abre
  cómics directamente. Se añaden a la biblioteca si no estaban.
- **Soporte NFS (sobre HTTP)** — configura la IP y la ruta de un servidor/NAS para
  abrir cómics desde la red local.
- **Caché de extracción con límite configurable** — los cómics extraídos se mantienen
  en caché hasta un tamaño máximo (por defecto **5 GB**); al llenarse se eliminan los
  más antiguos (FIFO).
- **Barra de desplazamiento vertical** opcional sobre listas y cuadrículas.

## Requisitos

- **Android 8.0 (API 26)** o superior.
- Para leer desde carpetas locales se requiere el permiso de **acceso a todo el
  almacenamiento** (Android 11+) o de lectura/escritura en versiones anteriores.

## Puesta en marcha (compilar e instalar)

Requisitos del entorno de desarrollo:

- **JDK 17**
- **Android Studio** o línea de comandos con variables de entorno configuradas
  (`ANDROID_HOME`).

Desde la raíz del proyecto (PowerShell en Windows):

```powershell
.\gradlew.bat assembleDebug      # compila el APK de depuración
.\gradlew.bat installDebug       # compila e instala en un dispositivo conectado
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk` y puede instalarse
manualmente. Para ejecutar los tests unitarios:

```powershell
.\gradlew.bat testDebugUnitTest
```

> **Nota:** el `versionCode` se incrementa automáticamente en **cada** compilación
> (el gradle lo gestiona en `app/version.properties`). No edites ese fichero a mano.
> La versión se muestra como `1.0.<versionCode>`.

### Abrir el proyecto en Android Studio

1. `File → Open` y selecciona la carpeta raíz del proyecto.
2. Espera a que Gradle sincronice (descargará las dependencias).
3. Con un dispositivo conectado (o un emulador) con depuración USB activada, pulsa
   **Run**.

## Uso

### Biblioteca (pestaña principal)

- **Añadir cómics**: pulsa el botón flotante (FAB) y elige un archivo `.cbz`/`.cbr`
  o **Escanear carpeta** para importar todos los cómics de un directorio (se recuerdan
  las carpetas escaneadas para futuros reescaneos).
- **Buscar y ordenar**: iconos de la barra superior. Selecciona el filtro
  (Todo / Título / Autor / Serie / Editorial / Arco) y el orden (Nombre / Ruta / Fecha).
- **Menú del cómic** (mantén pulsado): cambiar portada, editar metadatos o eliminar.
- **Actualizar biblioteca**: reescanea las carpetas guardadas (elimina entradas cuyo
  archivo ya no existe) y puede actualizar los metadatos leyendo `ComicInfo.xml` de
  todos los cómics, mostrando progreso y tiempo restante.
- Toca un cómic para empezar a leer desde la primera página.

### Lector

- Desliza a izquierda/derecha para cambiar de página.
- Pellizca para hacer zoom (**hasta 8x**); con zoom, arrastra con un dedo para moverte
  por la página.
- Sin zoom: toca la **esquina inferior izquierda** (atrás) o la **derecha** (adelante).
- Doble toque en el centro: zoom rápido; doble toque con zoom activo: restablecer.
- Un botón de retroceso y el indicador **“Página X / Y”** aparecen durante la carga.

### Historial

- Lista de las últimas lecturas, ordenada por fecha. Toca un elemento para **retomar
  la lectura** en la página guardada.
- Puedes eliminar un elemento individual o vaciar todo el historial.

### Carpetas

- Explorador de archivos que muestra los volúmenes de almacenamiento. Navega hasta un
  archivo `.cbz`/`.cbr` y tócalo para abrirlo directamente; se registra en la
  biblioteca de forma automática.

### Ajustes

- **Servidor NFS (red local)**: habilita el acceso a recursos compartidos e indica la
  IP y la ruta del recurso. La lectura se hace a través de `http://<ip>/<ruta>` (no
  hay cliente de protocolo NFS real).
- **Biblioteca**: número de elementos por fila en la cuadrícula (1–6).
- **Caché de lectura**: tamaño máximo de la caché de extracción (1–50 GB) y uso actual.
  Al reducir el límite, la caché se recorta automáticamente (FIFO).

## Formato de metadatos

La aplicación lee automáticamente `ComicInfo.xml` si el archivo CBZ/CBR lo incluye:
título, serie, número, editorial, guionista, dibujante y arco argumental. En ausencia
de este fichero, el título y el número se adivinan a partir del nombre del archivo
(p. ej. `Batman 012 (2020) (Digital)` → **Batman**, número **12**).

## Estructura del proyecto

Para desarrolladores y contribuidores: la referencia completa del código (mapa de
archivos, flujos frecuentes y convenciones) está en **[AGENTS.md](AGENTS.md)**.

## Licencia

Este proyecto está bajo la **GNU General Public License v3.0**. Consulta el archivo
[LICENSE](LICENSE).