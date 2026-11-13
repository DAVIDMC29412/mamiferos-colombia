# Mamíferos de Colombia — App Android (APK) + base de datos + panel de revisión

## Qué hace

**En el celular (funciona sin señal):**
1. Toma una foto o elige una o varias de la galería.
1b. **MegaDetector V6** revisa primero si hay un animal. Si no hay (persona, vehículo, paisaje, objeto), **no le pone nombre de especie** y pide otra foto. Si la persona sabe que sí hay un animal, toca **"Sí hay un animal: analizarla igual"**. Si hay animal, se recorta y se clasifica el recorte.
2. El modelo (MobileNetV3 destilado, 6,1 MB) dice la especie y muestra el top-3, sin internet.
3. Ajusta el resultado con la ubicación: por GPS (automático) o eligiendo el departamento.
4. La persona **confirma** la especie, **elige otra del top-3** o de la lista de 40, o, si no está, **escribe el nombre común** (y el científico, si lo sabe).
5. Todo queda **guardado en el celular** con fecha, GPS, departamento, equipo y nombre del observador.
6. **Apenas hay internet** (o solo wifi, si así se configura), sube sola la foto y sus datos a Firebase, aunque la app esté cerrada.
7. La pestaña **Comunidad** muestra las últimas fotos subidas desde todos los equipos.

**En internet:**
- **Firebase (plan gratuito Spark, sin tarjeta)** guarda los datos y las fotos. Todo va en **Firestore**: cada foto se reduce a 800 px y se guarda en la colección `fotos`; cada registro va en `registros` con una miniatura de 400 px. El plan gratuito da 1 GB, que alcanza para unas 8.000 fotos.
- El **panel web** (`panel/`) muestra todas las fotos, el mapa, el equipo que subió cada una, y permite a los revisores (ustedes o Cortolima) **validar o rechazar** cada foto y descargar las validadas para **reentrenar el modelo**.

```
MamiferosColombia/
├── app/                      ← la app Android (se abre con Android Studio)
│   └── src/main/assets/      ← modelo .tflite, 40 especies, factores por departamento
│                               y megadetector.tflite (101 MB, se copia a mano: no va en el ZIP)
├── firebase/                 ← reglas de seguridad de la base de datos
├── panel/                    ← página web de revisión (index.html + config.js)
└── firebase.json             ← para publicar el panel y las reglas con un comando
```

> **megadetector.tflite:** cópienlo en `app/src/main/assets/` antes de compilar. Si falta, la app funciona igual pero no revisa si hay un animal (en Ajustes aparece "No instalado"). Con él, el APK pesa unos 110 MB y el primer análisis tarda algunos segundos más.

---

## Parte 1. Compilar el APK (sin Firebase todavía)

La app compila y funciona **sin Firebase**: guarda todo en el celular y no sube nada. Así pueden probarla primero.

1. Instalen **Android Studio** (gratis): https://developer.android.com/studio
2. Abran Android Studio → **Open** → elijan la carpeta `MamiferosColombia`.
3. Esperen a que termine **Gradle Sync** (la primera vez descarga varias cosas; puede tardar 5–15 minutos).
   - Si Android Studio ofrece actualizar versiones ("AGP Upgrade Assistant"), pueden decir que no; el proyecto funciona con las versiones que trae.
4. Menú **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. Al terminar, clic en **locate**: el archivo es `app/build/outputs/apk/debug/app-debug.apk`.
6. Pasen ese APK al celular (cable, Drive, WhatsApp) y ábranlo. Android pedirá permitir **"instalar apps de origen desconocido"**: acepten.

**Para probar directo desde el computador:** activen en el celular *Opciones de desarrollador → Depuración USB*, conéctenlo por cable y presionen ▶ **Run** en Android Studio.

---

## Parte 2. Crear la base de datos en Firebase

1. Entren a https://console.firebase.google.com con una cuenta de Google → **Agregar proyecto** (por ejemplo, `mamiferos-colombia`).
2. **Agregar app Android:**
   - Nombre del paquete: **`co.mamiferos.colombia`** (debe ser exactamente este).
   - Descarguen **`google-services.json`** y cópienlo en la carpeta **`app/`** del proyecto (junto a `app/build.gradle.kts`).
3. **Authentication → Comenzar → Método de acceso** y activen:
   - **Anónimo** (lo usa la app; nadie tiene que registrarse).
   - **Correo electrónico/contraseña** (lo usan los revisores en el panel).
4. **Firestore Database → Crear base de datos** → modo **producción** → una región cercana (por ejemplo `southamerica-east1`).
   - Pestaña **Reglas**: borren lo que hay, peguen el contenido de `firebase/firestore.rules` y **Publicar**.
5. Vuelvan a Android Studio, hagan **Sync** y compilen otra vez el APK (Parte 1, paso 4). Ahora la app sí sube.

### Crear los revisores (ustedes o Cortolima)
1. **Authentication → Usuarios → Agregar usuario**: correo y contraseña del revisor.
2. Copien el **UID** que aparece en la lista.
3. **Firestore → Iniciar colección** → ID: `revisores` → ID del documento: **el UID copiado** → agreguen un campo `nombre` (texto) con el nombre del revisor → Guardar.
4. Repitan para cada revisor. Solo quienes estén en `revisores` pueden validar o rechazar fotos.

---

## Parte 3. El panel web

1. En la consola de Firebase: **Configuración del proyecto → Tus apps → Agregar app → Web (</>)**. Copien el objeto `firebaseConfig` y péguenlo en **`panel/config.js`**.
2. Abrir el panel en el computador (no funciona con doble clic; necesita un servidor):
   - **Opción rápida** (necesita Node.js): en la carpeta del proyecto, `npx serve panel` y abran la dirección que aparece (http://localhost:3000).
   - **Opción para compartir con Cortolima** (Firebase Hosting, gratis):
     ```
     npm install -g firebase-tools
     firebase login
     firebase use --add          (elijan su proyecto)
     firebase deploy
     ```
     Queda publicado en `https://SU-PROYECTO.web.app` y también se publican las reglas de `firebase/`.
     Firebase Hosting funciona en el plan gratuito.
3. En el panel:
   - **Entrar como revisor**: con el correo y la contraseña creados arriba.
   - **Solo ver las fotos**: cualquier persona, sin poder cambiar nada.
   - Muestra: número de fotos, cuántas faltan por revisar, especies nuevas propuestas, equipos distintos, mapa, y una tarjeta por foto con lo que dijo el modelo, lo que respondió la persona, el equipo y el observador.
   - El revisor elige la especie correcta y presiona **Validar**, o **Rechazar** si la foto no sirve.
   - **Descargar validadas (ZIP para reentrenar)**: un ZIP con una carpeta por especie y `metadata.csv`.

---

## Parte 4. Reentrenar el modelo con las fotos nuevas

1. En el panel: **Descargar validadas (ZIP para reentrenar)** → `fotos_validadas.zip`.
2. Descomprímanlo: queda `<especie>/<id>.jpg`, con la misma forma que `04_dataset`, y `metadata.csv`. Las especies que no están entre las 40 quedan en `_nuevas/<nombre>/`.
3. Agreguen esas carpetas al dataset, súbanlo a Kaggle como versión nueva y vuelvan a correr los dos notebooks (entrenamiento y ensamble/destilación).
   - Una especie nueva conviene agregarla como clase 41 cuando tenga al menos unas 100 fotos validadas.
4. Del notebook 2 (celda 11.2) salen `mamiferos.tflite`, `etiquetas.json` y `ubicacion_departamentos.json`. Reemplacen `app/src/main/assets/mamiferos.tflite`; si cambiaron las especies o los factores, actualicen también `especies.json` y `ubicacion.json`. Suban `versionCode` y `versionName` en `app/build.gradle.kts` y compilen.

**Modelo más pequeño (3,3 MB):** el notebook también generó la versión int8. Para usarla, reemplacen el `.tflite` por esa versión (mismo nombre `mamiferos.tflite`). Pierde unos 0,6 puntos de acierto (93,1 % frente a 93,7 % con ubicación).

---

## Cómo funciona por dentro

| Parte | Archivo | Qué hace |
|---|---|---|
| Modelo | `modelo/Clasificador.kt` | Carga el `.tflite`, reduce la foto a 224 × 224 y devuelve 40 probabilidades. |
| Ubicación | `modelo/Ubicacion.kt` | `p_final ∝ p × factor[departamento]^λ`, la misma fórmula del notebook (celda 9.5). |
| Procesar foto | `Procesador.kt` | Lee la foto, la endereza, guarda una copia de máx. 1.600 px, toma el GPS (de la foto o del celular), elige el departamento, clasifica y guarda. |
| Guardado local | `datos/Registro.kt` | Base de datos Room (SQLite) en el celular: cada foto con su resultado y su estado de subida. |
| Subida | `sync/SubirWorker.kt`, `sync/Fotos.kt` | WorkManager espera internet; sube la foto reducida (colección `fotos`) y los datos con miniatura (colección `registros`). Si la persona corrige después, se vuelve a subir solo el cambio. |
| Pantallas | `ui/` | Identificar, resultado (corregir / especie nueva), Mis fotos, Comunidad y Ajustes. |

**Departamento automático:** el GPS funciona sin internet, pero traducir las coordenadas a un nombre de departamento usa el servicio de Android, que normalmente necesita internet. Sin internet, la app usa el último departamento detectado y lo indica en el resultado; si nunca se ha detectado ninguno, no aplica el ajuste. En campo, en el Tolima, pueden elegir **Tolima** a mano en el selector. Las coordenadas siempre se guardan.

**Fotos de la galería:** se eligen con el selector de archivos de Android, que conserva el GPS guardado en la foto (cuando la foto lo tiene y se dio el permiso de ubicación de fotos).

**Datos que se suben por cada foto:** foto, fecha, latitud y longitud, departamento, top-3 con porcentajes, especie del modelo, respuesta de la persona (confirmada / corregida / nueva), nombre común y científico si es nueva, observador, equipo (marca, modelo y versión de Android), ID de instalación y versión del modelo.

---

## Siguientes fases

1. **YOLO dentro de la app:** si no detecta un animal, pedir otra foto; si hay una persona, no subirla; si hay un animal, recortarlo antes de clasificar (así se entrenó el modelo). Hay que elegir un detector pequeño y medir su tamaño y velocidad en el celular.
2. **Actualizar el modelo por internet:** que la app descargue el `.tflite` nuevo sin reinstalar.
3. **Departamento sin internet:** guardar dentro de la app los límites de los departamentos para detectarlo solo con el GPS.

---

## Problemas comunes

| Problema | Solución |
|---|---|
| Gradle Sync falla al descargar | Revisen el internet del computador y presionen *Try again*. |
| `File google-services.json is missing` | No debería pasar (se activa solo si existe), pero si aparece, copien el archivo a `app/` o compilen sin Firebase. |
| La app dice "Firebase no está configurado" | Falta `app/google-services.json` o no se volvió a compilar después de copiarlo. |
| Las fotos quedan "Error al subir" | Revisen que Authentication Anónimo esté activo y que las reglas de Firestore (incluida la parte de `fotos`) estén publicadas. La app reintenta sola. |
| El panel no carga | Debe abrirse con `npx serve` o Firebase Hosting, no con doble clic, y `config.js` debe tener los datos del proyecto. |
| Un revisor no puede validar | Su UID debe estar en la colección `revisores`. |
