import json
from pathlib import Path
import os
os.chdir(Path(__file__).resolve().parents[2])

S={'type':'string'}
N={'type':'integer'}
def E(*values): return {'type':'string','enum':list(values)}
def obj(fields, required=None): return {'type':'object','properties':fields,'required':list(fields) if required is None else required,'additionalProperties':False}
catalog=[]
def tool(name,component,method,path,description,fields=None,required=None):
    args={}
    if '{id}' in path: args['id']={**S,'description':'Identificador existente obtenido en una consulta o alta'}
    if fields is not None: args['datos']=obj(fields,required)
    catalog.append(dict(name=name,component=component,method=method,path=path,description=description,schema=obj(args)))
def crud(component,plural,singular,path,fields,required=None,update_fields=None):
    tool('listar_'+plural,component,'GET',path,'Consultar todos los '+plural)
    tool('buscar_'+singular,component,'GET',path+'/{id}','Consultar '+singular+' por ID')
    tool('crear_'+singular,component,'POST',path,'Dar de alta '+singular+'. Devuelve el ID generado por DonaTrack.',fields,required)
    tool('modificar_'+singular,component,'PUT',path+'/{id}','Modificar '+singular+' existente respetando las reglas y referencias del componente.',fields if update_fields is None else update_fields,required)
    tool('eliminar_'+singular,component,'DELETE',path+'/{id}','Dar de baja '+singular+' existente. Puede rechazarse si está en uso.')

crud('donaciones','productos','producto','/productos',dict(nombre=S,descripcion=S,categoriaID=S,subcategoriaID=S,identificadorID=S),['nombre','descripcion','categoriaID'])
crud('donaciones','categorias','categoria','/categorias',dict(nombre=S,descripcion=S,categoriaPadreID=S),['nombre','descripcion'],update_fields=dict(nombre=S,descripcion=S))
crud('donaciones','identificadores','identificador','/identificadores',dict(tipo=E('CODIGODEBARRAS','QR'),descripcion=S))
crud('donadores','donadores','donador','/donadores',dict(nombre=S,apellido=S,edad=N,email=S,nroDocumento=S,domicilio=S))
crud('donadores','entidades','entidad','/entidades',dict(razonSocial=S,domicilio=S,telefono=S,correo=S))
crud('donadores','necesidades','necesidad','/necesidades',dict(entidadID=S,productoSolicitadoID=S,descripcion=S,cantidadObjetivo=N,nivelDeUrgencia=N,tipo=E('EXTRAORDINARIA','RECURRENTE')))
crud('logistica','depositos','deposito','/depositos',dict(nombre=S,direccion=S,capacidadMaxima=N))
crud('incentivos','insignias','insignia','/insignias',dict(nombre=S,descripcion=S))
crud('incentivos','misiones','mision','/misiones',dict(nombre=S,insigniaID=S,categoriaInicio=E('OCASIONAL','COLABORADOR','TRANSFORMADOR','SALVADOR','REVOLUCIONARIO'),categoriaFin=E('OCASIONAL','COLABORADOR','TRANSFORMADOR','SALVADOR','REVOLUCIONARIO'),tipo=E('COMPLETITUD','DONACIONES_EXITOSAS','DONACIONES_ASCENDENTES','REVOLUCION_DONADORA')))
tool('listar_donaciones','donaciones','GET','/donaciones','Consultar donaciones registradas')
tool('buscar_donacion','donaciones','GET','/donaciones/{id}','Consultar el estado de una donación')
tool('realizar_donacion','donaciones','POST','/donaciones','Registrar una donación de un producto existente a un depósito con matchmaking configurado. DonaTrack valida al donador y procesa la asignación asincrónicamente.',dict(donadorID=S,depositoID=S,productoID=S,descripcion=S,cantidad=N))
tool('historial_donacion','donaciones','GET','/donaciones/{id}/historial','Consultar trazabilidad de estados de la donación')
tool('registrar_queja','donaciones','POST','/donaciones/{id}/quejas','Registrar una queja sobre una donación ya entregada (ACEPTADA). Actualiza donación y donador.',dict(descripcion=S))
tool('estadisticas_donador','donadores','GET','/donadores/{id}/estadisticas','Obtener estado, categoría, insignias y misión actual del donador')
tool('quejas_donador','donadores','GET','/donadores/{id}/quejas','Consultar historial de quejas del donador')
tool('estado_donador','donadores','PATCH','/donadores/{id}/estado','Establecer manualmente el estado de un donador',dict(estado=E('VERIFICADO','SOSPECHOSO','BANEADO')))
tool('consultar_stock','logistica','GET','/stock/{id}','Consultar cantidad disponible del producto identificado por id')
tool('listar_asignaciones','logistica','GET','/asignaciones','Consultar asignaciones, estados, paquetes y origen (matchmaking o solicitud de entidad)')
tool('buscar_asignacion','logistica','GET','/asignaciones/{id}','Consultar una asignación por ID')
tool('reportar_entrega','logistica','POST','/entregas','Reportar entrega de un paquete asignado: completa asignación, satisface necesidad y acepta la donación.',dict(id=S))
tool('reportar_entrega_lote','logistica','POST','/entregas/lote','Reportar una única entrega física de varios paquetes asignados a la misma necesidad. Para recurrentes el lote debe cubrir el objetivo completo.',dict(paqueteIds={'type':'array','items':S}))
tool('periodo_necesidad','donadores','GET','/necesidades/{id}/periodo','Consultar si una necesidad es semanal o mensual y el inicio del período vigente')
tool('crear_necesidad_mensual','donadores','POST','/necesidades?periodo=MENSUAL','Registrar una necesidad recurrente mensual. DonaTrack valida el producto y reserva stock si alcanza el objetivo.',dict(entidadID=S,productoSolicitadoID=S,descripcion=S,cantidadObjetivo=N,nivelDeUrgencia=N,tipo=E('RECURRENTE')))
tool('configurar_matchmaking','logistica','PATCH','/depositos/{id}/algoritmo','Configurar algoritmo del depósito antes de recibir donaciones.',dict(algoritmo=E('SUB_ATENDIDOS','PRIORIDAD_POR_SCORE')))
tool('procesar_donador','incentivos','POST','/procesamiento/{id}','Evaluar misiones y actualizar categoría e insignias; también revoca donaciones exitosas si hay quejas.')
tool('asignar_mision','incentivos','POST','/misiones/donador/{id}','Asignar al donador id una misión del catálogo; respeta categoría y secuencia.',dict(id=S))
tool('asignar_insignia','incentivos','POST','/insignias/donador/{id}','Asignar manualmente una insignia del catálogo a un donador registrado.',dict(id=S))
tool('mision_donador','incentivos','GET','/misiones/donador/{id}','Consultar misión en curso del donador (sin contenido si no tiene misión activa)')
tool('insignias_donador','incentivos','GET','/insignias/donador/{id}','Consultar insignias obtenidas por el donador')
for component in ['donaciones','donadores','logistica','incentivos']:
    tool('salud_'+component,component,'GET','/actuator/health','Consultar disponibilidad del componente '+component)
    tool('metricas_'+component,component,'GET','/actuator/metrics','Consultar nombres de métricas disponibles en '+component)
tool('ficha_integral_donador','consultas','GET','/donadores/{id}/ficha','Ficha combinada desde MongoDB: categoría e historial, insignias, misión y progreso, donaciones por estado, quejas y entidades beneficiadas. Consistencia eventual: si acabás de operar, consultá de nuevo sin repetir el comando de escritura.')
tool('necesidades_urgentes','consultas','GET','/necesidades/urgentes','Necesidades sin cubrir ordenadas por urgencia, con stock disponible por depósito y unidades reservadas sin entregar.')
catalog[-1]['schema']=obj({'filtros':obj(dict(urgenciaMinima=N,tipo=E('RECURRENTE','EXTRAORDINARIA'),categoriaID=S,subcategoriaID=S,limite=N),[])},[])
tool('reporte_depositos','consultas','GET','/depositos/reporte','Ocupación, algoritmo, asignaciones por estado, entidades atendidas y tiempo medio desde ingreso hasta última entrega. El rango filtra fecha de ingreso. Fechas ISO yyyy-MM-dd o fecha-hora.')
catalog[-1]['schema']=obj({'filtros':obj(dict(depositoID=S,desde=S,hasta=S),[])},[])
tool('calidad_donaciones','consultas','GET','/calidad','Calidad por categoría y subcategoría (donaciones CONQUEJA / total de donaciones) y donadores con más quejas, estado y categoría.')
catalog[-1]['schema']=obj({'filtros':obj(dict(limiteDonadores=N),[])},[])
tool('estado_sistema','consultas','GET','/estado','Estado del modelo de lectura, cantidad de fichas, necesidades, depósitos y eventos procesados. No confundir consistencia eventual con falla de una operación.')
tool('salud_consultas','consultas','GET','/actuator/health','Comprobar salud de Consultas y conexión a MongoDB.')
tool('salud_gateway','gateway','GET','/actuator/health','Comprobar disponibilidad del punto de entrada único.')
tool('subcategorias_de_categoria','donaciones','GET','/categorias/{id}/subcategorias','Listar las subcategorías de una categoría antes de clasificar un producto.')
tool('donaciones_de_donador','donaciones','GET','/donaciones','Consultar donaciones de un donador desde una fecha ISO yyyy-MM-dd.')
catalog[-1]['schema']=obj({'filtros':obj(dict(donadorID=S,fecha=S))})
tool('asignar_stock_a_necesidad','logistica','POST','/stock/asignaciones','Reservar stock disponible para una necesidad existente. La reserva no significa entrega.',dict(necesidadId=S,productoId=S,cantidad=N))
for operation in catalog:
    readonly=operation['method']=='GET'
    operation['annotations']=dict(readOnlyHint=readonly,destructiveHint=operation['method']=='DELETE',idempotentHint=readonly,openWorldHint=True)
    if not readonly:
        operation['description']+=' Confirmá con el usuario los datos y referencias antes de modificar. Ante timeout verificá el estado antes de reintentar.'

targets = ['mcp-server/src/main/resources/tools.json']
if Path('telegramBot').is_dir():
    targets.append('telegramBot/untitled/src/main/resources/tools.json')
if Path('testing').is_dir():
    targets.append('testing/contratos/tools.json')
for target in targets:
    p=Path(target); p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'{len(catalog)} herramientas y comandos documentados')
