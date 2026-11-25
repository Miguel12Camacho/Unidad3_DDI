# Matriz de cumplimiento — SmartClass

| Requisito | Evidencia |
|---|---|
| Aula inteligente | Panel de grupo, gestión de dispositivos, asistencia y avisos. |
| Funciones IoT del aula | Iluminación en tres zonas, ventilador, aire acondicionado, proyector, sensores ambientales, movimiento, puertas/ventanas y alarma. |
| Demostración sin hardware | Modo de simulación con estados, sensores y registro de eventos locales. |
| Preparación para conexión física | Gateway REST con modo memoria y backend Home Assistant; mapeo de sensores/actuadores y órdenes al servicio/entity configurado. Requiere HA y dispositivos integrados; contrato SmartClass en `API_GATEWAY.md`. |
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
| Android TV / Google TV | `LEANBACK_LAUNCHER`, leanback/touchscreen opcionales, banner xhdpi 320×180, orientación horizontal y experiencia separada con `androidx.tv:tv-material`. |
| LG webOS TV | App webOS `platforms/webos`, paquete `.ipk`, botones D-pad y panel conectado al gateway Home Assistant. Instalación de desarrollo con Developer Mode; sin prueba en TV física. |
| Samsung Tizen TV | Cliente web compartido y proyecto `platforms/tizen`; se puede servir el panel al navegador del TV. La instalación como app launcher necesita SDK/certificado Tizen del propietario y no se incluye paquete firmado. |
| Datos teléfono-TV | Importación manual del respaldo JSON operativo desde TV; excluye cuentas y contraseñas. Sincronización de red o emparejamiento quedan como mejora futura. |
| Compilación y pruebas unitarias | `testDebugUnitTest assembleDebug` completado; ocho pruebas pasan. Evidencia: `INFORME_PRUEBAS.md`. |
| Pruebas gateway TV | Cuatro pruebas Python cubren modo memoria, mapeo Home Assistant mock, autorización y página web; el paquete webOS se generó como `.ipk`. No se probó con Home Assistant real ni TV física. |
| Pruebas visuales y de TV | No se ejecutó ningún emulador por instrucción del usuario. La navegación remota y presentación real de webOS se deben validar en un TV físico. |
| Código organizado | Estado/navegación en `MainActivity.kt`; pantallas en `Screens.kt`, `ClassroomScreen.kt` y `AcademicManagementScreen.kt`; modelos académicos en `AcademicData.kt`; contrato IoT en `ClassroomGateway.kt`; componentes en `Components.kt`. |
| APK | `app/build/outputs/apk/debug/app-debug.apk`. |

El encendido configurado se ejecuta al iniciar manualmente una sesión en la app. La app no ejecuta tareas de encendido cuando está cerrada.

## Compatibilidad y alcance físico

El modo físico usa el contrato REST y puede accionar entidades que Home Assistant ya integre y tenga mapeadas; no descubre ni vuelve controlable cualquier marca automáticamente. El `.ipk` webOS y el cliente Tizen son apps web separadas del APK Android. La instalación webOS en esta entrega es por Developer Mode; publicar en LG Apps requiere pasar la aprobación de LG. La app no debe usarse como sistema de alarma certificado ni para funciones de seguridad vital.
