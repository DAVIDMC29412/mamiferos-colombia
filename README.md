# Mamíferos de Colombia

Clasificación de **40 especies de mamíferos colombianos** en fotos de cámaras trampa, con una app Android que funciona **sin internet**. Proyecto de impacto regional pensado para apoyar el monitoreo de fauna de **Cortolima** en el Tolima.

**Integrantes:** Jaime Andrés Ortega Cadena · David Alejandro Muñoz Carmona — Universidad Cooperativa de Colombia · Bienal BIING 2026

## Resultados principales (2.458 fotos de prueba de Colombia, nunca vistas)
| Modelo | Accuracy | F1 macro | Tamaño | Latencia CPU |
|---|---|---|---|---|
| Ensamble 2× EfficientNetV2-S + ubicación (profesor) | 95,36 % | 0,941 | 630,5 MB | 2.357,8 ms |
| MobileNetV3-Large destilado + ubicación, TFLite float16 (**la app**) | 93,69 % | — | 6,1 MB | 3,7 ms |

## Estructura
| Carpeta | Contenido |
|---|---|
| `notebooks/` | Cuadernos de Kaggle: entrenamiento, ensamble, ubicación, L1/L2, destilación, MegaDetector y las entregas S10, S14 y S15 |
| `docs/` | Informes en PDF y la presentación de la Bienal |
| `app/` | App Android (Kotlin + Jetpack Compose + TensorFlow Lite) |
| `panel/` | Panel web para validar fotos (Firebase Hosting) |
| `firebase/` | Reglas de Firestore |
| `reentrenar/` | Script para reentrenar con las fotos validadas |

## Cómo funciona la app
1. **MegaDetector V6** revisa si en la foto hay un animal. Si no hay, no inventa una especie.
2. Recorta el animal y el **modelo destilado** dice la especie, con su top-3.
3. Ajusta la respuesta con el **departamento** donde se tomó la foto.
4. Guarda todo en el celular y lo sube a **Firebase** cuando hay conexión.

## Archivos que no están en el repositorio
- `app/google-services.json`: la llave de Firebase de cada equipo.
- `app/src/main/assets/megadetector.tflite`: 101 MB, supera el límite de GitHub. Se genera con `notebooks/03_MegaDetector_a_TFLite.ipynb`.

Instrucciones detalladas de compilación en `LEEME.md`.
