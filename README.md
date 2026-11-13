# Mamíferos de Colombia

App Android que identifica **40 especies de mamíferos colombianos** en fotos de cámaras trampa, **en segundos y sin internet**. Propuesta dirigida a **Cortolima** (Corporación Autónoma Regional del Tolima) para apoyar el monitoreo de fauna.

**🎬 Video del pitch:** ENLACE_DEL_VIDEO
**📱 Descargar la app (APK):** [Releases](https://github.com/DAVIDMC29412/mamiferos-colombia/releases)

## Integrantes
| Nombre | ID |
|---|---|
| Jaime Andrés Ortega Cadena | 828281 |
| David Alejandro Muñoz Carmona | 821249 |

Universidad Cooperativa de Colombia · Bienal BIING 2026

## El problema
Las cámaras trampa registran la fauna de día y de noche, y cada una genera miles de fotos. Revisarlas a mano (separar las vacías y decir qué especie aparece) toma semanas. Además, en campo casi nunca hay señal, así que una solución en la nube no sirve.

## La solución
1. **MegaDetector V6** revisa si en la foto hay un animal. Si no hay, la app no inventa una especie.
2. Un **MobileNetV3-Large destilado** (6,1 MB, TFLite float16) dice la especie, con su confianza y el top-3.
3. La respuesta se ajusta con el **departamento** donde se tomó la foto (GPS).
4. Todo queda en el celular y se sube a **Firebase** cuando hay conexión. Desde un panel web, Cortolima valida las fotos para reentrenar el modelo.

## Métricas (2.458 fotos de prueba de Colombia, nunca vistas en el entrenamiento)
| Modelo | Accuracy | F1 macro | Top-3 | Tamaño | Latencia |
|---|---|---|---|---|---|
| Ensamble 2× EfficientNetV2-S + ubicación (profesor) | 95,36 % | 0,941 | 98,82 % | 630,5 MB | 2.357,8 ms / foto (CPU) |
| **MobileNetV3-Large destilado + ubicación (app)** | **93,65 %** | **0,921** | **97,97 %** | **6,1 MB** | **3,7 ms (CPU) · 35 ms en el celular** |

- **Tres semillas:** 93,21 % ± 0,23 de accuracy · F1 0,914 ± 0,004.
- **Umbral «no estoy seguro» = 0,61:** por encima, la app acierta el 97,4 % de las fotos.
- **Prueba en la app** (60 fotos de la región, Samsung SM-M156B): 92,2 % de accuracy y 96,1 % en top-3 en las 51 fotos analizadas. Respuesta total de 2,8 s (mediana), incluido MegaDetector.
- **Límites:** confunde especies hermanas (ñeque / ñeque negro, ardilla andina / colirroja, zaino / pecarí labiado), pierde acierto con poca luz (−26 puntos) y desenfoque (−14), y MegaDetector descartó 9 de 60 fotos con animal. Detalles en [`docs/S15_Informe_Auditoria_Ortega_Munoz.pdf`](docs/S15_Informe_Auditoria_Ortega_Munoz.pdf).

## Estructura
| Carpeta | Contenido |
|---|---|
| `notebooks/` | **`Notebook_Final_Ortega_Munoz.ipynb`** (entrenamiento, destilación, conversión a TFLite y evaluación), auditoría S15 y entregas S10 / S14 |
| `model/` | `mamiferos.tflite` (modelo de la app), `labels.txt` (40 clases en el orden de salida del modelo), `labels_nombres.txt` y `especies.json` |
| `app/` | Código fuente de la app Android (Kotlin, Jetpack Compose, TensorFlow Lite) |
| `docs/` | Informe de auditoría, deck del pitch, tablas S10 / S14 y capturas |
| `panel/` · `firebase/` · `reentrenar/` | Panel web de validación, reglas de Firestore y script de reentrenamiento |

## Cómo instalar la app
1. Descargue `App_MamiferosColombia.apk` desde [Releases](https://github.com/DAVIDMC29412/mamiferos-colombia/releases).
2. En el celular Android (8.0 o superior), ábralo y permita **instalar apps de origen desconocido**.
3. Abra **Mamíferos de Colombia** y acepte los permisos de cámara y ubicación.
4. Toque **Tomar foto** o **Subir fotos**. Funciona en modo avión.

## Cómo compilar y ejecutar
**App**
1. Abra la carpeta del proyecto en **Android Studio** (Gradle JDK: Temurin 21).
2. Copie en `app/src/main/assets/` el archivo `megadetector.tflite`. Pesa 101 MB, más que el límite de GitHub; se genera con `notebooks/03_MegaDetector_a_TFLite.ipynb`.
3. *(Opcional)* Para sincronizar con la nube, ponga su `google-services.json` de Firebase en `app/`.
4. **Build → Build APK(s)**.

**Notebooks**
Se ejecutan en **Kaggle** con GPU T4, con el dataset `mamiferos-colombia-40` como entrada.

Más detalles en [`LEEME.md`](LEEME.md).
