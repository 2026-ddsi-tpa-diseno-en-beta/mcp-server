# DonaTrack MCP · Entrega 6

Cliente Java 21 para Claude Desktop, con transporte stdio y 84 herramientas. Opera los cuatro módulos mediante el API Gateway y consulta los reportes integrados de MongoDB. Las reglas de negocio permanecen en las APIs.

## Inicio

```sh
mvn package
python scripts/probar_stdio.py
python scripts/crear_config_claude.py --gateway http://localhost:8080
```

El generador comprueba Java 21 y el JAR y escribe `target/claude-desktop.config.json`. Incorporar la entrada a la configuración de Claude Desktop y reiniciar el cliente. `stdout` se reserva para JSON-RPC; los diagnósticos van a `stderr`.

Configurar `GATEWAY_URL` con el gateway local o desplegado. Las variables antiguas `DONACIONES_API_URL`, `DONADORES_API_URL`, `LOGISTICA_API_URL`, `INCENTIVOS_API_URL` y `CONSULTAS_API_URL` quedan como overrides para pruebas; retirarlas de la configuración de demo para garantizar que todo pase por el gateway.

## Herramientas

- ABM: productos, categorías/subcategorías, identificadores, donadores, entidades, necesidades, depósitos, insignias y misiones.
- Operaciones: donación, entrega individual/en lote, queja, procesamiento de donador, reserva de stock, misión/insignia y algoritmo del depósito.
- Consultas integradas: `ficha_integral_donador`, `necesidades_urgentes`, `reporte_depositos`, `calidad_donaciones`.
- Diagnóstico: estado del modelo de lectura, salud de gateway/Consultas/cuatro APIs y nombres de métricas.
- Trazabilidad: historial de donación, quejas, stock, asignaciones y donaciones de un donador desde una fecha.

Las herramientas indican si leen o modifican datos y si son destructivas. Los parámetros se validan contra el esquema antes de llamar al servicio. Los filtros se codifican como query params; los IDs no pueden modificar la ruta. No se exponen borrados masivos, callbacks internos ni las claves de reconstrucción.

Cada respuesta incluye texto legible y `structuredContent` con status, traceId y resultado completos. Los errores conservan `isError=true`; no se presentan escrituras fallidas como éxitos. Ante un timeout de escritura, consultar antes de reintentar: MCP no promete idempotencia de comandos que el backend no garantiza.

## Pedidos para la demo

1. «Mostrame las necesidades urgentes sin cubrir y el stock de cada producto por depósito.»
2. «Prepará una donación de 10 unidades del producto P del donador D al depósito Z. Confirmemos los datos antes de enviarla.»
3. «Consultá las asignaciones y explicame cuál corresponde a esa donación. Todavía no la marques entregada.»
4. «Reportá la entrega del paquete y después mostrame la ficha integral del donador.»
5. «Mostrame la calidad por categoría y los donadores con más quejas.»
6. «Mostrame ocupación, algoritmo y tiempo medio de entrega del depósito entre estas fechas.»

Los reportes tienen consistencia eventual: después de un comando puede haber una demora. Repetir una lectura no implica repetir la escritura.

## Catálogo compartido

`scripts/generar_catalogo.py` mantiene `tools.json` de MCP, Telegram y `testing/contratos`. Desde TPA, regenerar también Postman con `python testing/entrega6/generar_postman.py`. El catálogo es el contrato de transporte compartido; no sustituye las validaciones de dominio.

El ensayo completo está en `testing/entrega6/probar_demo.py --mcp`. Usa gateway, RabbitMQ y workers reales. La prueba stdio aislada valida protocolo y transporte con un HTTP stub; no demuestra conexión con Claude Desktop.
