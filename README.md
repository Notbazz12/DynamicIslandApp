# 📱 Dynamic Island for Android

Una implementación ultra-minimalista, fluida y altamente integrada de la **Isla Dinámica (Dynamic Island)** para dispositivos Android. Inspirada en la estética limpia de **Apple** y **Nothing OS**.

---

## ✨ Características Premium

### 🎨 Music Theme Matching (Extracción de Color)
La isla extrae automáticamente el **color vibrante dominante de la carátula** de la música que estás escuchando (compatible con Spotify, YouTube Music, etc.) y pinta la barra de progreso y el visualizador de ese color de manera adaptativa.

### 🌊 Ecualizador Dinámico (Waveform)
- En la píldora compacta, se muestra un ecualizador de ondas animado que responde al estado de la música (se mueve al reproducir, se congela al pausar).
- Al expandir la isla, obtienes una vista completa con la carátula, barra de progreso delgada estilo iOS y controles de playlist completos (`Anterior ⏮`, `Play/Pausa ▶/⏸`, `Siguiente ⏭`).

### 🔋 Animación de Carga de Batería
Al conectar el cargador, la isla se estira suavemente revelando un rayo verde esmeralda y el porcentaje de batería actual antes de regresar a su forma original de manera orgánica.

### 🔊 Indicador de Modos de Sonido
Muestra un banner animado con íconos personalizados al alternar entre los perfiles de sonido de tu teléfono:
- 🔕 **Silencio** (Campana cruzada en rojo)
- 📳 **Vibración** (Celular vibrando en gris)
- 🔔 **Sonido activo**

### 🎮 Modo Gaming & Ajuste en Redes Sociales (Mini Mode)
- **Modo Gaming**: Reduce la isla a un micro-punto invisible en la pantalla para que no estorbe al jugar. Actívalo en la app o con un **toque largo en la isla**.
- **Modo Redes Sociales**: La isla detecta automáticamente cuando estás en Instagram, TikTok, Twitter o YouTube y se encoge, desactivando su auto-expansión para no tapar los contenidos.

---

## 📸 Captura de Pantalla / Instalación

Para instalar el archivo `.apk` y evitar el bloqueo de **"Ajustes Restringidos"** de Android (debido a la instalación externa):

1. Descarga el archivo `app-debug.apk` de la sección de **Releases** de este repositorio.
2. Abre el archivo e instálalo.
3. Ve a **Ajustes de tu celular** -> **Aplicaciones** -> **Ver todas** -> **Dynamic Island**.
4. En la esquina superior derecha, toca los **tres puntos** y selecciona **"Permitir ajustes restringidos"**.
5. Abre la aplicación y activa todos los permisos. ¡Disfruta la experiencia!

---

## 🛠 Requisitos de Compilación

Si deseas compilar el código fuente por ti mismo:
- Android SDK (API 34)
- JDK 17 o superior
- Gradle 8.2

Ejecuta el siguiente comando para generar el APK de lanzamiento:
```bash
./gradlew assembleDebug
```

---

## ☕ Donativos

Si te gusta este proyecto y quieres apoyar su desarrollo independiente, puedes invitarme a un café:

- **Ko-fi**: [ko-fi.com/notdevp](https://ko-fi.com/notdevp)
- **PayPal**: [paypal.me/tu-usuario](https://paypal.me)

---
*Desarrollado de forma independiente con fines de personalización y diseño estético.*
