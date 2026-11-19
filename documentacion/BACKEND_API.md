# Contrato de sincronización SmartClass

La app guarda los datos operativos en Room y permite exportar/restaurar un JSON local. La sincronización entre teléfonos requiere desplegar un servidor escolar; la app no incluye un backend ni crea cuentas en la nube.

## Seguridad esperada

- Publicar únicamente por HTTPS con TLS moderno.
- Validar el token Bearer en cada solicitud y asociar el token a una escuela/tenant.
- Aplicar autorización por rol y grupo en el servidor; nunca confiar en el rol enviado por el cliente.
- Validar tamaños, esquema y relaciones antes de aceptar importaciones.
- Mantener contraseñas, hashes de acceso y códigos de recuperación fuera de las copias operativas.
- Registrar usuario, fecha, operación y resultado en bitácora del servidor.
- Implementar límites de frecuencia y respuestas que no revelen si una cuenta existe.

## Endpoints

### `POST /api/v1/sync/push`

Encabezados: `Authorization: Bearer <token>` y `Content-Type: application/json`.

Cuerpo JSON exportado por SmartClass, formato `smartclass-backup-v1`. Respuesta mínima:

```json
{"accepted": true, "syncId": "uuid", "serverTime": "2026-11-19T12:00:00Z"}
```

Debe validar el payload y guardar una versión auditable. No se deben sobrescribir usuarios ni credenciales con datos del cliente.

### `GET /api/v1/sync/pull`

Mismos encabezados. Devuelve el objeto de respaldo operativo aceptado más recientemente para esa escuela. La app lo importa después de subir los cambios locales; el servidor debe definir la política de conflictos (esta primera versión usa el respaldo completo más reciente).

## Recuperación y cuentas

El alta inicial de administrador es local a cada instalación y la recuperación actual usa un código de un solo uso que se muestra al crear la cuenta. Para recuperación por correo, inicio de sesión compartido, revocación de sesiones y sincronización de usuarios, el servidor deberá proveer endpoints de identidad propios (por ejemplo OAuth/OIDC o API con tokens breves y renovables). Las credenciales locales de Room no son válidas para autenticación remota.

## Versión del esquema

El payload lleva `format: smartclass-backup-v1`. Cambios incompatibles requieren versionar el formato y soportar la migración o rechazo explícito en servidor y cliente.
