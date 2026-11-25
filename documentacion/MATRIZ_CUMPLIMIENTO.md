# Matriz de cumplimiento — SmartClass

| Requisito | Evidencia |
|---|---|
| Aula inteligente | Panel de grupo, gestión de dispositivos, asistencia y avisos. |
| Funciones IoT del aula | Iluminación en tres zonas, ventilador, aire acondicionado, proyector, sensores ambientales, movimiento, puertas/ventanas y alarma. |
| Demostración sin hardware | Modo de simulación con estados, sensores y registro de eventos locales. |
| Preparación para conexión física | Modo físico, estado conectado/desconectado, lectura de gateway y comandos HTTPS; contrato en `API_GATEWAY.md`. |
| Historial de operaciones | Bitácora local por aula con hora, comandos, lecturas y errores recientes. |
| Administración de estudiantes | CRUD y búsqueda por nombre/matrícula; los datos se conservan en almacenamiento local. |
| Grupos y docentes | Crear/editar grupos, registrar docentes y asignar docente responsable. |
| Perfil docente y permisos | Inicio de sesión local en Room; las cuentas docentes quedan limitadas a sus grupos asignados en la aplicación. |
| Asistencia ampliada | Presentes, retardos, faltas, justificaciones con motivo e historial por fecha. |
| Exportación y estadísticas | Exportación CSV por grupos y estadísticas individuales de asistencia, retardo y faltas. |
| Planeación de clases | Materias, horarios semanales, aula/grupo/docente y equipos configurados para iniciar. |
| Conflictos de horario | Valida traslapes del mismo grupo, docente o aula en un mismo día. |
| Próximas y anteriores | Calcula próximas ocurrencias y conserva sesiones iniciadas/finalizadas. |
| Inicio contextual | Muestra horario, grupo, lista, asistencia y estado de equipos de la clase. |
| Centro de notificaciones | Categorías, filtros de fecha/prioridad, estado leído/resuelto e historial local. |
| Alertas del aula | Temperatura alta, desconexión, puerta fuera de horario, alarma y errores del gateway. |
| Recordatorios | Clases próximas, entregas y mantenimiento; alarmas locales del dispositivo. |
| Diseño adaptable | Navegación inferior compacta y lateral en ventanas amplias. |
| Android TV / Google TV | `LEANBACK_LAUNCHER`, leanback y touchscreen opcionales, banner xhdpi 320×180, orientación horizontal para TV, márgenes seguros y experiencia separada con `androidx.tv:tv-material`; clase/ambiente, asistencia, cartelera, sensores y control por mosaicos/escenas. |
| Datos teléfono-TV | Importación manual del respaldo JSON operativo desde TV; excluye cuentas y contraseñas. Sincronización de red o emparejamiento quedan como mejora futura. |
| Compilación y pruebas unitarias | `testDebugUnitTest assembleDebug` completado; ocho pruebas pasan. Evidencia: `INFORME_PRUEBAS.md`. |
| Pruebas visuales y de TV | No ejecutadas por instrucción del usuario de no hacer emulaciones. No hay capturas reales; D-pad y presentación en una TV deben verificarse en dispositivo si se requieren evidencias visuales. |
| Código organizado | Estado/navegación en `MainActivity.kt`; pantallas en `Screens.kt`, `ClassroomScreen.kt` y `AcademicManagementScreen.kt`; modelos académicos en `AcademicData.kt`; contrato IoT en `ClassroomGateway.kt`; componentes en `Components.kt`. |
| APK | `app/build/outputs/apk/debug/app-debug.apk`. |

El encendido configurado se ejecuta al iniciar manualmente una sesión en la app. La app no ejecuta tareas de encendido cuando está cerrada.

## Compatibilidad y alcance físico

El modo físico comunica con un gateway de escuela a través del contrato HTTPS documentado. Un gateway debe traducir protocolos de fabricantes y mapear los IDs de dispositivos/sensores a un aula. No existe un protocolo universal que permita controlar, sin configuración, cualquier marca de luminaria, HVAC, proyector o sensor. La compatibilidad real requiere instalar el gateway, configurar red y credenciales, y probar cada equipo. El soporte de TV corresponde a Android TV/Google TV; no convierte el APK Android en una app para Tizen, webOS u otros sistemas propietarios. La app no debe usarse como sistema de alarma certificado ni para funciones de seguridad vital.
