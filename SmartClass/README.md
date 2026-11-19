# SmartClass — Aplicación Android

Aplicación nativa para Android desarrollada con Kotlin y Jetpack Compose.

## Funciones incluidas
- Pantalla de inicio con resumen de asistencia, dispositivos y horario.
- Panel de aula para controlar de forma demostrativa iluminación, ventilación, proyector y alarma.
- Ajuste de temperatura simulado.
- Registro de asistencia con conteo y porcentaje.
- Avisos del grupo y edición del comunicado principal.

**Nota:** los controles son demostrativos. No controlan dispositivos físicos ni conservan los cambios al cerrar la aplicación.

## Requisitos
- Android Studio.
- JDK 17.
- Android SDK Platform 35.
- Emulador o dispositivo Android con API 26 o superior.

## Cómo unir las tres partes
1. Descarga los tres ZIP: `SmartClass_Parte_1_Base.zip`, `SmartClass_Parte_2_Modulo.zip` y `SmartClass_Parte_3_Codigo.zip`.
2. Crea una carpeta llamada `SmartClass`.
3. Extrae el contenido de cada ZIP en esa misma carpeta. Cuando Windows pregunte si deseas combinar carpetas, acepta. Si pregunta por archivos duplicados, conserva los archivos; las partes están organizadas para no duplicar los archivos principales.
4. Al terminar, la carpeta `SmartClass` debe contener `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `README.md` y la carpeta `app`.
5. En Android Studio selecciona **Open** y abre la carpeta `SmartClass`.
6. Espera la sincronización de Gradle. Si Android Studio solicita instalar componentes del SDK, acepta.
7. Selecciona un emulador o conecta un dispositivo Android y pulsa **Run**.

## Generar APK
En Android Studio selecciona **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Si la compilación termina correctamente, el APK de depuración suele quedar en `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura principal
- `app/src/main/java/com/smartclass/app/MainActivity.kt`: pantallas y lógica de la aplicación.
- `app/src/main/AndroidManifest.xml`: configuración de Android.
- `app/src/main/res/`: recursos visuales y tema.
- `app/build.gradle.kts`: configuración del módulo y dependencias.
