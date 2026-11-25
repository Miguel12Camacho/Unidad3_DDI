# Integración de dispositivos físicos

SmartClass conserva un modo de simulación para presentaciones. El modo físico usa un adaptador REST HTTPS independiente de la interfaz. Para admitir equipo de distintas marcas, instala en la red de la escuela un gateway que traduzca los protocolos disponibles (por ejemplo, controladores de iluminación, Modbus, BACnet o APIs del fabricante) al contrato común de abajo. La app no descubre ni controla directamente dispositivos de fabricantes desconocidos.

## Configuración

En **Aula > Físico > Configuración** define la URL HTTPS del gateway, el identificador del aula y opcionalmente un token Bearer. El token solo se conserva en memoria mientras la app permanece abierta. Para producción, el gateway debe tener un certificado TLS válido y accesible desde el dispositivo Android. En el APK debug, un servidor local de demostración puede usar `http://10.0.2.2:8765` desde el emulador; tanto la política de URL como la configuración de seguridad de red restringen HTTP a ese host. No se permite HTTP en release ni HTTP hacia otros hosts.

IDs de actuadores que debe aceptar el gateway:

| ID | Equipo |
|---|---|
| `light_front` | Luces zona frontal |
| `light_center` | Luces zona central |
| `light_back` | Luces zona posterior |
| `fan_main` | Ventilador |
| `ac_main` | Aire acondicionado |
| `projector` | Proyector |
| `security_alarm` | Alarma |

IDs de conexión de sensores: `sensor_temperature`, `sensor_humidity`, `sensor_air`, `sensor_motion`, `sensor_door`, `sensor_window`.

## Lectura de estado

`GET /api/v1/classrooms/{classroomId}/state`

Respuesta JSON:

```json
{
  "devices": {
    "light_front": { "state": "on", "connected": true },
    "fan_main": { "state": "off", "connected": true },
    "projector": { "state": "on", "connected": false }
  },
  "sensors": {
    "temperatureC": 23.5,
    "humidityPercent": 47,
    "airQualityPpm": 620,
    "motionDetected": true,
    "doorOpen": false,
    "windowOpen": false,
    "connected": {
      "sensor_temperature": true,
      "sensor_humidity": true,
      "sensor_air": true,
      "sensor_motion": true,
      "sensor_door": true,
      "sensor_window": false
    }
  }
}
```

Todos los valores del sensor pueden omitirse si el equipo no está disponible; la app los mostrará como desconocidos. `connected` indica el estado de red del dispositivo, no solo el estado del relé.

## Comandos

`POST /api/v1/classrooms/{classroomId}/devices/{deviceId}/command`

Cabecera: `Content-Type: application/json`; opcional `Authorization: Bearer <token>`.

```json
{ "state": "on" }
```

`state` admite `on` y `off`. Devuelve un estado HTTP 2xx al aceptar el comando. Después SmartClass vuelve a consultar el estado completo para confirmar la lectura. Cualquier error HTTP se presenta en el panel y se registra en el historial local.

El gateway debe autenticar y autorizar comandos por aula, validar los IDs, aplicar límites de seguridad del HVAC/actuadores, proteger secretos y registrar las operaciones de forma durable para producción. El historial que muestra la app es local al dispositivo Android y solo conserva los últimos eventos recientes.

## Gateway local y conexión física

`scripts/demo_gateway.py` sirve el contrato y la interfaz SmartClass TV en `/`. Sin configuración Home Assistant usa estados in-memory y no controla hardware. Para conectar dispositivos reales, consulta [`TV_WEBOS_HOME_ASSISTANT.md`](TV_WEBOS_HOME_ASSISTANT.md): configura Home Assistant, el mapeo de entidades y los secretos de servidor, y arranca el proceso con `--host 0.0.0.0` en una red local controlada. El modo físico requiere una `SMARTCLASS_CLIENT_TOKEN`; la interfaz web la envía como Bearer. El token de Home Assistant nunca se envía al televisor.

El backend consulta `/api/states` de Home Assistant y ejecuta el servicio configurado por actuador (`POST /api/services/{domain}/{service}`). El resultado es una orden real a una entidad integrada de Home Assistant, no garantía de movimiento mecánico; verifica la lectura reportada y el dispositivo. Usa HTTPS/reverse proxy y restringe la red al desplegar fuera de una demostración local.
