# SmartClass — aplicación para aula inteligente

Proyecto Android nativo desarrollado con Kotlin y Jetpack Compose.

## Funciones
- Panel con indicadores de asistencia, dispositivos activos, horario y estado de la clase.
- Navegación lateral en pantallas amplias y barra inferior en pantallas compactas.
- Control demostrativo de iluminación, ventilación, proyector, alarma y temperatura.
- Registro de asistencia con porcentaje, selección individual y acciones para marcar o limpiar el grupo.
- Avisos y recordatorios; el aviso principal se puede editar.
- Preferencias locales para conservar avisos, asistencia y estados de dispositivos al volver a abrir la app.
- Gestión escolar: alta, edición, baja y búsqueda de estudiantes por nombre o matrícula.
- Creación de grupos y asignación de docentes.
- Perfil docente de demostración, limitado en la interfaz a sus grupos asignados.
- Registro diario de presentes, retardos, faltas y faltas justificadas con motivo.
- Historial de asistencia, estadísticas individuales y exportación CSV.
- Materias y horarios semanales con aula, grupo y docente asignados.
- Detección de traslapes del mismo grupo, docente o aula.
- Próximas clases, sesiones activas e historial de clases finalizadas.
- Equipos configurables para encender al iniciar una sesión; la sesión muestra horario, lista del grupo y estado del aula.
- Centro de notificaciones con filtros de categoría, fecha y prioridad.
- Alertas por temperatura elevada, equipos desconectados, puerta abierta fuera de clase, alarma activa y errores de conexión.
- Recordatorios programados de clases, entregas y mantenimiento con notificación del dispositivo.
- Panel del aula con iluminación por tres zonas, ventilador, aire acondicionado, proyector, alarma, sensores ambientales y de seguridad, conexiones e historial.
- Modo de simulación para demostración y modo físico preparado para conectarse mediante un gateway escolar HTTPS compatible.
- Diseño adaptable a tamaños compactos y amplios, distintas orientaciones y ventanas redimensionables.

> En simulación, los estados son locales y no accionan hardware. El modo físico requiere instalar y configurar un gateway IoT que implemente el contrato REST de `documentacion/API_GATEWAY.md`; SmartClass no puede conectarse directamente a equipos de cualquier marca sin esa integración. Usa una red escolar protegida y HTTPS.
>
> La gestión académica guarda sus datos en el dispositivo. Los perfiles administrador/docente son un flujo de demostración local, no una frontera de seguridad: para uso escolar real, agrega inicio de sesión y permisos por rol en un servidor.
>
> Los equipos programados se encienden al iniciar la sesión desde la app. El encendido automático con la app cerrada requiere un servicio Android persistente y permisos de alarmas; aún no está habilitado. En modo físico, el gateway escolar debe estar accesible y aceptar los comandos.
>
> Android solicita permiso para mostrar notificaciones del sistema. El centro dentro de la app sigue disponible si el permiso se deniega. Los eventos de sensores físicos dependen de lecturas recibidas del gateway.

## Compatibilidad
La aplicación es Android nativa y se adapta a teléfonos y tabletas Android, así como a ventanas redimensionables en ChromeOS y emuladores Android para PC o laptop. Para ejecutar una versión nativa en Windows/macOS/Linux se necesita un cliente de escritorio o una versión web. Los relojes Wear OS requieren una interfaz y un módulo específicos que no están incluidos en este proyecto.

## Requisitos
- Android Studio reciente, JDK 17, Android SDK Platform 35.
- Emulador recomendado: Pixel Tablet o Pixel 7, API 35.

## Abrir y ejecutar
1. En Android Studio selecciona **Open** y elige la carpeta `SmartClass`.
2. Acepta la sincronización de Gradle e instala los componentes del SDK solicitados.
3. En **Device Manager** crea un emulador Pixel Tablet o Pixel 7 con Android API 35.
4. Pulsa **Run**.

## Generar APK
Android Studio: **Build > Build Bundle(s) / APK(s) > Build APK(s)**. El APK de depuración aparece normalmente en `app/build/outputs/apk/debug/app-debug.apk`.

## Capturas requeridas
Ejecuta la app y guarda PNG reales en `capturas/`: Inicio, Control del aula, Asistencia, Avisos y diálogo de edición.

## Estructura
- `app/src/main/java/com/smartclass/app/MainActivity.kt`: estado, persistencia local y navegación adaptable.
- `app/src/main/java/com/smartclass/app/Screens.kt`: panel, aula, asistencia y avisos.
- `app/src/main/java/com/smartclass/app/AcademicManagementScreen.kt`: administración académica y flujos de asistencia.
- `app/src/main/java/com/smartclass/app/AcademicData.kt`: modelos y persistencia local de estudiantes, grupos, docentes y registros.
- `app/src/main/java/com/smartclass/app/ClassScheduleScreen.kt`: materias, horarios, conflictos, próximas clases y sesiones.
- `app/src/main/java/com/smartclass/app/Notifications.kt`: reglas de alertas, persistencia, envío al sistema y alarmas locales.
- `app/src/main/java/com/smartclass/app/NotificationCenterScreen.kt`: centro y filtros, además de recordatorios de entregas/mantenimiento.
- `app/src/main/java/com/smartclass/app/ClassroomScreen.kt`: panel físico/simulado, zonas, sensores, conexiones e historial.
- `app/src/main/java/com/smartclass/app/ClassroomGateway.kt`: interfaz de gateway y adaptador REST estándar.
- `app/src/main/java/com/smartclass/app/AcademicManagementScreen.kt`: gestión de estudiantes, grupos, perfiles docentes, asistencia, historial, estadísticas y CSV.
- `app/src/main/java/com/smartclass/app/AcademicData.kt`: modelos y persistencia local de los datos académicos.
- `app/src/main/java/com/smartclass/app/Components.kt`: encabezados, tarjetas e indicadores compartidos.
- `app/src/main/AndroidManifest.xml`: configuración y compatibilidad de ventanas Android.
- `documentacion/`: matriz de cumplimiento e indicaciones de entrega.
- `documentacion/API_GATEWAY.md`: contrato para integrar hardware y gateways de distintos fabricantes.

## Cuentas, almacenamiento y sincronización
- La primera ejecución solicita crear una cuenta administradora y un código único para recuperar acceso. Las cuentas, contraseñas derivadas con PBKDF2 y roles viven en Room; la recuperación actual es local al dispositivo.
- El perfil docente se asigna a una cuenta y queda limitado a los grupos de esa persona en la interfaz.
- Room modela usuarios, estudiantes, docentes, grupos, aulas, materias, horarios, clases, asistencia, dispositivos, lecturas, alertas, avisos y bitácora.
- Administradores pueden exportar/restaurar respaldos JSON. El respaldo operativo excluye usuarios, contraseñas y códigos de recuperación.
- Sincronización HTTPS incluida como cliente; requiere desplegar un servidor compatible con `documentacion/BACKEND_API.md`. La autenticación compartida, recuperación por correo y sincronización multi-dispositivo no operan hasta contar con ese backend.
