# LectorNotificacionesRoy_NUEVO_COMPILABLE

Proyecto Android listo para compilar con GitHub Actions.

Funciones conservadas de la versión solicitada:
- Lee notificaciones de las aplicaciones seleccionadas.
- El selector muestra aplicaciones instaladas, incluidas aplicaciones del sistema.
- Cuando detecta un pago, pronuncia: "CLIENTE PAGÓ" + el monto.
- Mantiene el retraso de 2 segundos y las demás funciones existentes.

## GitHub
Sube TODO el contenido de esta carpeta al repositorio. El workflow incluido detecta automáticamente la carpeta que contiene `settings.gradle` y compila `app-debug.apk`.


## Parlante activo v2
- Pip real de 700 ms cada 2 minutos.
- Amplitud del pip aumentada a ~7% para mejorar la detección por parlantes con autoapagado agresivo.
- Las notificaciones reales reinician el contador de 2 minutos.
