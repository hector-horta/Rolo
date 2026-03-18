# 📇 Rolo - Business Card Manager

¡Bienvenido a Rolo! Una aplicación de gestión de tarjetas de presentación de alta velocidad construida con las últimas tecnologías de Android.

## 📁 Estructura del Proyecto

Esta es la arquitectura de archivos generada según los requerimientos:

```text
/Users/hhorta/Dev/Rolo/
│
├── README.md                                 # Este archivo con la documentación y estructura general
└── app/src/main/java/com/rolo/app/
    ├── MainActivity.kt                       # Composable principal con Navigation, Paywall y TopAppBar dinámica
    ├── data/                                 # Capa de Persistencia
    │   ├── Database.kt                       # (Recomendado) Room Database configuration
    │   └── Dao.kt                            # (Recomendado) Data Access Objects
    ├── ml/                                   # Capa de Machine Learning (OCR local)
    │   └── TextRecognitionHelper.kt          # Lógica con Google ML Kit Text Recognition y Regex básicos
    └── ui/                                   # Capa de Interfaz y Estados
        ├── MainViewModel.kt                  # StateHolder principal, maneja contador de cards, pagos y lógica
        └── theme/                            # Material 3 Dynamic Colors (Configuración estándar de Compose)
```

## 🚀 Tecnologías Implementadas en los Archivos Entregados:
1. **Material You (M3)**: Usando el core Compose Material 3 API, soportando `dynamicLightColorScheme` para la adaptación al sistema. UI Minimalista y limplia en MainActivity.
2. **Google ML Kit Offline**: Usamos el `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` que permite hacer OCR al instante y gratis en el dispositivo, previniendo costos de API.
3. **Regex Heuristics para Contactos**: Implementados en el TextRecognitionHelper para separar de forma cruda emails, teléfonos, calles y nombres del bloque de texto devuelto por ML Kit.
4. **Intents de Sistema**: La UI tiene botones dedicados para abrir Teléfono, Gmail y Google Maps al dar clic a un dato extraído de la tarjeta.
5. **StateFlow y UiState**: Arquitectura pura Unidirectional Data Flow en el `MainViewModel`.
6. **Limitador y Paywall Minimalista**: La TopAppBar está recubierta por una _LinearProgressIndicator_ que se torna roja al alcanzar el límite (25) y una simple pantalla Paywall pidiendo el pago de $1.99.

## 🛠 Siguientes Pasos (Para el Desarrollador):
* Configurar Room Database agregando la entidad `BusinessCard` mapeada en el paquete `/data/`.
* Agregar tu logica real de `BillingClient` en el método `purchasePremium` del ViewModel para Google Play Billing.
* Sustituir el Intent de imagen `TakePicturePreview` del Activity con API completa de **CameraX** junto a un ImageAnalyzer para auto-fotografiar la tarjeta en cuanto el objeto rectangular se detecte (esto hará la captura de fotos "automática" como en los requerimientos).
* Ajustar el SAF (Storage Access Framework) para exportar la BD Sqlite generada por Room a Google Drive (`onExportDb`).

¡Disfruta desarrollando Rolo! 🚀
