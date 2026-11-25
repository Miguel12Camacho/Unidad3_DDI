# SmartClass — aplicación para aula inteligente

Proyecto Android nativo desarrollado con Kotlin y Jetpack Compose.

## Funciones
- Panel con indicadores de asistencia, dispositivos activos, horario y estado de la clase.
- Navegación lateral en pantallas amplias, barra inferior en móviles e interfaz independiente para Android TV/Google TV con Jetpack Compose for TV (`androidx.tv:tv-material`).
- Panel de clase para TV con materia, docente, grupo, aula, horario, reloj en vivo y tiempo restante; modo ambiente con próxima clase y avisos.
- Pantallas de consulta de asistencia y cartelera rotativa; estado del aula con temperatura, humedad, puerta, movimiento y alertas visibles.
- Mosaicos grandes manejables con D-pad para luces por zonas, clima, proyector y alarma, más las escenas Iniciar clase y Terminar clase.
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
> La gestión académica y las cuentas se guardan localmente en Room. El acceso por rol se valida en la aplicación; la sincronización compartida y la autenticación remota requieren un servidor escolar.
>
> Los equipos configurados se encienden al iniciar manualmente una sesión desde la app. La aplicación no ejecuta encendidos programados cuando está cerrada. En modo físico, el gateway escolar debe estar accesible y aceptar los comandos.
>
> Android solicita permiso para mostrar notificaciones del sistema. El centro dentro de la app sigue disponible si el permiso se deniega. Los eventos de sensores físicos dependen de lecturas recibidas del gateway.

## Compatibilidad
La aplicación Android se adapta a teléfonos, tabletas y ventanas redimensionables en ChromeOS. En Android TV/Google TV abre una experiencia independiente horizontal con TV Material. También hay un paquete webOS instalable para televisores LG y un proyecto Tizen compartido para Samsung; ambos usan HTML/CSS/JavaScript y D-pad. Los archivos de plataforma y conexión se documentan en [`documentacion/TV_WEBOS_HOME_ASSISTANT.md`](documentacion/TV_WEBOS_HOME_ASSISTANT.md). Son clientes distintos del APK Android.

## Requisitos
- Android Studio reciente, JDK 17, Android SDK Platform 35.
- Emuladores recomendados: Pixel Tablet o Pixel 7 (API 35), y Android TV con imagen **Television** para validar launcher, foco y control D-pad.
- Python 3 para iniciar el gateway de demostración local.

## Primera ejecución
No hay un usuario o una contraseña de prueba preconfigurados. Al abrir por primera vez, crea el administrador del dispositivo con nombre, correo y una contraseña de **al menos 10 caracteres que incluya letras y números**. Guarda el código de recuperación que aparece después de crear la cuenta; no se puede volver a consultar. Las cuentas son locales al dispositivo y se conservan al cerrar la app. Para volver a ver el formulario de primer administrador en una instalación de demostración, borra los datos de SmartClass desde la configuración de Android o desinstala y vuelve a instalar la app.

## Abrir y ejecutar
1. En Android Studio selecciona **Open** y elige la carpeta `SmartClass`.
2. Acepta la sincronización de Gradle e instala los componentes del SDK solicitados.
3. En **Device Manager** crea un emulador Pixel Tablet/Pixel 7 con API 35 o un perfil Android TV con imagen **Television** si quieres revisar visualmente la interfaz.
4. Pulsa **Run**.

## Generar APK
Android Studio: **Build > Build Bundle(s) / APK(s) > Build APK(s)**. El APK de depuración aparece normalmente en `app/build/outputs/apk/debug/app-debug.apk`.

## Gateway de demostración y equipos reales
`python scripts/demo_gateway.py` sirve el contrato REST en memoria para pruebas sin hardware. Para una demostración física, configura Home Assistant, crea un mapeo de entidades y ejecuta el servidor con `SMARTCLASS_HA_URL`, `SMARTCLASS_HA_TOKEN`, `SMARTCLASS_HA_MAPPING` y `SMARTCLASS_CLIENT_TOKEN`; las instrucciones completas están en [`documentacion/TV_WEBOS_HOME_ASSISTANT.md`](documentacion/TV_WEBOS_HOME_ASSISTANT.md). Home Assistant debe integrar previamente cada equipo. La variante física envía servicios reales y requiere mapear los IDs de luz, ventilador, clima, proyector y alarma a entidades del aula. Expón el gateway solo en una red local aislada.

`Iniciar clase` en Aula enciende las tres zonas de luz, proyector y ventilación/clima; `Terminar clase` los apaga. La alarma de seguridad queda sin cambios. Al finalizar una sesión desde Clases se apagan luces, clima y proyector; si falla el gateway, la app muestra un aviso y el estado se debe revisar manualmente.

El panel Smart TV también se sirve desde el gateway en `/` y ofrece estado, sensores, controles individuales y escenas con el control remoto. Para webOS se genera `build/webos/com.smartclass.tv_1.0.0_all.ipk` con el CLI oficial; la instalación en una TV se realiza con Developer Mode. No se ha probado en un LG físico en esta entrega.

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
- `app/src/main/res/drawable-xhdpi/tv_banner.png`: banner 320×180 para el launcher Android TV.
- `app/src/main/java/com/smartclass/app/TvExperienceScreen.kt`: experiencia independiente para Android TV/Google TV.
- `documentacion/`: matriz de cumplimiento y contratos técnicos.
- `documentacion/API_GATEWAY.md`: contrato para integrar hardware y gateways de distintos fabricantes.

## Datos compartidos entre teléfono y TV
Los datos se guardan localmente por dispositivo. Para llevar los datos capturados en el teléfono a la TV, exporta desde el teléfono un respaldo JSON operativo, transfiere el archivo a una ubicación accesible para Android TV (USB, almacenamiento compartido o proveedor de archivos) y usa **Importar respaldo** en el menú lateral de TV. El respaldo operativo no contiene usuarios ni contraseñas. La TV muestra sus datos locales hasta importar un respaldo nuevo; la sincronización de red y el emparejamiento por código quedan como mejora futura.

## Validación para TV
La experiencia usa `androidx.tv:tv-material`, con controles grandes y foco visible en los botones. La navegación prevista es: flechas entre menú y mosaicos, OK para ejecutar y Atrás para volver a Clase/ambiente. El manifiesto incluye `LEANBACK_LAUNCHER`, soporte Leanback y pantalla táctil opcionales, y banner xhdpi de 320×180. La orientación horizontal se fija al detectar una TV.

No se ejecutó un emulador ni se tomaron capturas de pantalla en esta entrega; la verificación se limitó a compilación y pruebas unitarias. El informe en [`documentacion/INFORME_PRUEBAS.md`](documentacion/INFORME_PRUEBAS.md) detalla esa limitación.

## Capturas
No se incluyen capturas porque no se ejecutó la app en un emulador o dispositivo durante esta revisión. La compilación no se presenta como evidencia visual.

## Cuentas, almacenamiento y sincronización
- La primera ejecución solicita crear una cuenta administradora y un código único para recuperar acceso. Las cuentas, contraseñas derivadas con PBKDF2 y roles viven en Room; la recuperación actual es local al dispositivo.
- El perfil docente se asigna a una cuenta y queda limitado a los grupos de esa persona en la interfaz.
- Room modela usuarios, estudiantes, docentes, grupos, aulas, materias, horarios, clases, asistencia, dispositivos, lecturas, alertas, avisos y bitácora.
- Administradores pueden exportar/restaurar respaldos JSON. El respaldo operativo excluye usuarios, contraseñas y códigos de recuperación.
- El cliente de sincronización usa HTTPS. El servidor escolar debe implementar el contrato de `documentacion/BACKEND_API.md` para compartir datos entre dispositivos y autenticar cuentas remotamente.
