# Evidencia técnica para el informe

## 20.4 Verificación de compilación y pruebas

### 20.4.1 Compilación

El 25 de noviembre de 2026 se ejecutó desde la raíz del proyecto:

```powershell
.\gradlew.bat --gradle-user-home .gradle '-Pkotlin.compiler.execution.strategy=in-process' testDebugUnitTest assembleDebug --console=plain
```

Resultado: **BUILD SUCCESSFUL**. Se generó `app/build/outputs/apk/debug/app-debug.apk` (20,004,749 bytes). La compilación incluyó el código, el manifest, los recursos de TV, el icono adaptativo y las configuraciones de red debug/release.

### 20.4.2 Pruebas unitarias y gateway local

Resultado de `testDebugUnitTest`: **8 pruebas aprobadas, 0 fallidas** (`ScheduleRulesTest`). Cubren horarios contiguos e inválidos, detección de conflictos, destinos HTTP permitidos solo en debug, variación de telemetría con umbral de alerta, resumen de asistencia por grupo/fecha, clase actual y tiempo restante, y siguiente clase en modo ambiente.

Se ejecutaron cuatro pruebas de `scripts/test_demo_gateway.py`: estado y comandos del modo memoria, lectura simulada y llamada de servicio Home Assistant mediante un HTTP mock, autorización del cliente, y conversiones de sensores. También se empaquetó la app webOS con el CLI oficial; resultado: `build/webos/com.smartclass.tv_1.0.0_all.ipk` (10,924 bytes). Estas pruebas validan el adaptador y el paquete, no la conexión a una instalación Home Assistant ni el accionamiento de hardware real.

### 20.4.3 Ejecución visual y capturas

No se ejecutó un emulador ni un televisor físico en esta revisión. La instrucción del usuario fue no hacer emulaciones. Por lo tanto, no hay capturas reales ni se afirma haber comprobado el D-pad, el foco y los márgenes en un LG webOS físico.

El cliente webOS ofrece estado de aula, lecturas, alertas, mosaicos/escenas y conexión al gateway con control remoto. Para completar evidencia visual y física, instala el `.ipk` en una TV LG webOS de destino, conéctala al gateway/Home Assistant y verifica que un control se refleje tanto en la TV como en la entidad física. No se enviaron comandos a equipos reales durante esta revisión.
