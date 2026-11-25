# SmartClass TV para webOS y control físico

## Qué se entrega

- `platforms/webos/` es un proyecto webOS TV y se empaqueta como `.ipk` con webOS CLI.
- `platforms/tizen/` es la variante Tizen TV; comparte la interfaz y declara el perfil/privilegio de control remoto.
- `platforms/web-shared/` contiene la interfaz web compartida que también sirve el gateway.
- `scripts/demo_gateway.py` conserva el modo de demostración in-memory y agrega modo físico mediante Home Assistant.

La aplicación Android no se ejecuta como APK en webOS. En un LG se instala el paquete webOS; en un Samsung Tizen se instala un paquete Tizen firmado, o se abre el panel desde el navegador del televisor apuntando al gateway. La interfaz maneja flechas, OK y Atrás; el foco queda resaltado. El panel web muestra sensores y equipos, escenas Iniciar/Terminar clase y errores de conexión.

## Instalar en un televisor LG webOS

Requiere una TV LG con webOS TV, una computadora en la misma red y la aplicación **Developer Mode** de LG instalada en el televisor. Esta entrega no se publicó en LG Apps; se instala directamente en la TV para demostración.

1. Instala Node.js y el [webOS CLI oficial](https://webostv.developer.lge.com/develop/tools/cli-installation): `npm install -g @webos-tools/cli`.
2. En la TV inicia **Developer Mode**, habilita el modo desarrollador y anota su dirección IP y passphrase.
3. Registra el dispositivo con `ares-setup-device` y crea un destino, por ejemplo `lg-aula`.
4. Empaqueta desde la raíz del proyecto: `ares-package platforms/webos -o build/webos`.
5. Instala el `.ipk` generado: `ares-install -d lg-aula build/webos/com.smartclass.tv_1.0.0_all.ipk`.
6. Inicia la app: `ares-launch -d lg-aula com.smartclass.tv`.
7. En **Gateway**, introduce la URL del equipo que ejecuta `demo_gateway.py`, por ejemplo `http://192.168.1.20:8765`, y la clave de cliente configurada en ese servidor.

La app usa HTML, CSS y JavaScript estándar para webOS TV. La compatibilidad visual final se debe confirmar en el modelo y versión webOS de destino; no se ejecutó un simulador ni se probó una TV física en este equipo de desarrollo.

## Abrir en Samsung Tizen

El navegador del televisor puede abrir `http://IP_DEL_SERVIDOR:8765` si TV y servidor están en la misma red. La UI compartida ya incorpora interacción por control remoto. Para instalar una app Tizen desde el launcher se necesita el SDK de Samsung, un perfil/certificado de firma propio y empaquetar el proyecto de `platforms/tizen`; esta entrega incluye el proyecto base, pero no un `.wgt` firmado.

## Conectar Home Assistant a equipos reales

Home Assistant debe tener integrados los equipos físicos (por ejemplo luces, ventilador, climate/HVAC, proyector mediante un enchufe o IR y sensores). SmartClass manda servicios Home Assistant explícitos sobre las entidades mapeadas; no descubre hardware ni convierte un dispositivo no integrado en controlable.

1. En Home Assistant crea un token de acceso de larga duración para la cuenta que controlará el aula.
2. Copia `scripts/home_assistant_mapping.example.json` a `scripts/home_assistant_mapping.json` y reemplaza cada `entity_id` por el ID exacto de Home Assistant. Revisa los servicios de encendido/apagado de cada entidad. El ejemplo de aire usa `climate/set_hvac_mode` y `cool`/`off`; ajústalo al HVAC de la escuela.
3. En PowerShell, define la URL, token y mapeo en la computadora que servirá como gateway:

```powershell
$env:SMARTCLASS_HA_URL = 'http://192.168.1.30:8123'
$env:SMARTCLASS_HA_TOKEN = 'TOKEN_DE_HOME_ASSISTANT'
$env:SMARTCLASS_HA_MAPPING = (Join-Path (Get-Location) 'scripts\home_assistant_mapping.json')
$env:SMARTCLASS_CLIENT_TOKEN = 'CLAVE_LARGA_PARA_LA_TV'
python scripts/demo_gateway.py --host 0.0.0.0
```

4. Abre en webOS **Gateway** y configura `http://IP_DE_LA_COMPUTADORA:8765`, `aula-204` y la clave `SMARTCLASS_CLIENT_TOKEN`.
5. Usa **Actualizar estado**, prueba un equipo y confirma la lectura en Home Assistant y en el dispositivo. Las escenas envían comandos en secuencia; si una orden falla, la pantalla lo indica y se debe revisar el aula.

El modo físico exige URL, token Home Assistant, archivo de mapeo y clave de cliente. La clave de Home Assistant solo vive en el proceso gateway; la TV recibe la clave de cliente. El puerto `8765` se debe exponer solo en la LAN aislada de la demostración, nunca en Internet. Para despliegues escolares, usa HTTPS/reverse proxy, reglas de firewall y una cuenta de Home Assistant limitada a las entidades del aula. Un comando aceptado por HA no garantiza que el relé o equipo haya completado físicamente la acción: comprueba el estado reportado y conserva controles manuales.

El servidor sin variables `SMARTCLASS_HA_*` inicia únicamente el modo in-memory de demostración y no acciona equipos reales.
