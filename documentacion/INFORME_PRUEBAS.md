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

El servidor `scripts/demo_gateway.py` también se verificó localmente: `GET` devolvió actuadores/sensores; un `POST` apagó `light_front` y la lectura siguiente confirmó el estado. Esto comprueba el contrato de demostración, no hardware físico.

### 20.4.3 Ejecución visual y capturas

No se ejecutó un emulador ni un dispositivo en esta revisión, conforme a la instrucción del usuario de no hacer emulaciones. Por lo tanto, no hay capturas reales y la navegación D-pad, el foco en pantalla y el recorte de imagen en una TV no se presentan como verificados visualmente. El APK compilado y las pruebas unitarias no reemplazan esa evidencia visual.

La TV implementada incluye una experiencia independiente con panel de clase/modo ambiente, asistencia, avisos rotativos, estado y control de aula, importación de respaldo JSON y controles TV Material. Para completar la evidencia visual en una entrega futura, se puede instalar el APK en Android TV real o en un emulador **Television**, recorrer cada sección con el control remoto y guardar las capturas.
