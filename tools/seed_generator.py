#!/usr/bin/env python3
"""Genera el bloque de datos de ejemplo de schema.sql.

Regenera el tramo de schema.sql que hay entre los marcadores
`-- >>> DATOS_GENERADOS_INICIO <<<` y `-- >>> DATOS_GENERADOS_FIN <<<`:

  * Tickets con ciclo de vida coherente: creacion -> asignacion ->
    (pendiente de usuario) -> resolucion -> cierre, en horario laboral.
  * fecha_limite = fecha_creacion + sla_horas de la prioridad final
    (el servicio recalcula el SLA cuando se escala una prioridad).
  * Historial_Ticket refleja cada cambio de estado, asignacion y escalation.
  * Comentarios alineados con el estado (el tecnico pide datos, el usuario
    resuelve, el tecnico resuelve, el usuario confirma el cierre).
  * Valoraciones de los tickets cerrados y algunos adjuntos de ejemplo.

Uso:  python tools/seed_generator.py   (reescribe schema.sql in situ)

Determinista: la semilla fija hace que dos ejecuciones produzcan exactamente
el mismo SQL, util para revisar los cambios en el diff del repositorio.
"""

from __future__ import annotations

import random
from datetime import datetime, timedelta
from pathlib import Path

SEED = 20261011
# El helpdesk opera con soporte continuo: el generador reparte tickets por todo
# la semana, incluidos fines de semana (turnos de guardia).
AHORA = datetime(2026, 10, 11, 17, 0, 0)

RAIZ = Path(__file__).resolve().parent.parent
SCHEMA = RAIZ / "schema.sql"
MARCADOR_INICIO = "-- >>> DATOS_GENERADOS_INICIO <<<"
MARCADOR_FIN = "-- >>> DATOS_GENERADOS_FIN <<<"

SLA_HORAS = {1: 72, 2: 24, 3: 8, 4: 4}  # Baja, Media, Alta, Critica
ESTADOS = {1: "Abierto", 2: "En_Progreso", 3: "Pendiente_Usuario",
           4: "Resuelto", 5: "Cerrado"}
USUARIOS = {1: "Marta Sanchez Lopez", 2: "Javier Torres Ruiz",
            3: "Laura Gomez Martin", 4: "Diego Fernandez Ibanez",
            5: "Alejandro Ortiz Vega"}

rng = random.Random(SEED)


# ---------------------------------------------------------------------------
# Copy realista por categoria: (titulo, descripcion)
# ---------------------------------------------------------------------------

INCIDENCIAS = {
    1: [  # Hardware
        ("El ordenador no enciende tras el corte de luz",
         "Tras el corte de luz de esta manana, el equipo de sobremesa no responde al pulsar el boton de encendido. El piloto de red electrica esta apagado."),
        ("La impresora de planta 2 no responde",
         "La impresora compartida de la planta 2 aparece como desconectada para todos los usuarios de esa zona desde esta manana."),
        ("Portatil que se apaga solo al desconectar el cargador",
         "El portatil se apaga instantaneamente al retirar el cargador aunque la bateria indique un 70 por ciento."),
        ("Monitor con lineas verticales despues de un golpe",
         "El monitor presenta tres lineas verticales permanentes desde que golpeo accidentalmente la pantalla al mover la mesa."),
        ("El teclado no detecta algunas teclas",
         "Varias teclas del teclado USB no responden al pulsarlas, concretamente las letras G, H y la barra espaciadora."),
        ("Raton inalambrico que se desconecta cada pocos minutos",
         "El raton pierde la conexion de forma intermitente y hay que volver a emparejarlo cada pocos minutos."),
        ("El ventilador del equipo hace mucho ruido",
         "Desde hace unos dias el ventilador del equipo emite un ruido fuerte y continuo que resulta molesto en la oficina."),
        ("Docking station que no reconoce la segunda pantalla",
         "Al conectar el portatil al docking station la segunda pantalla no se detecta, aunque el cargador y la red si funcionan."),
        ("Escaner de la sala de juntas no alimenta papel",
         "El escaner de la sala de juntas enciende pero no toma papel al intentar digitalizar documentos."),
        ("Altavoz USB sin sonido en reuniones",
         "El altavoz USB del salon de actos no emite sonido en las reuniones, aunque el sistema lo reconoce como dispositivo."),
    ],
    2: [  # Software
        ("No se abre la aplicacion de gestion tras la actualizacion",
         "Despues de la ultima actualizacion la aplicacion de gestion cierra inmediatamente sin mostrar ningun mensaje de error."),
        ("El programa de contabilidad pide activarse cada dia",
         "Cada manana al abrir el programa de contabilidad vuelve a pedir la clave de activacion aunque se guardo ayer."),
        ("Errores al exportar informes a Excel",
         "Al exportar cualquier informe a Excel el archivo se genera vacio o con las columnas desalineadas."),
        ("La aplicacion va muy lenta al abrir multiples ventanas",
         "Con mas de tres ventanas abiertas la aplicacion se bloquea durante varios segundos y no responde al raton."),
        ("Falta una opcion de menu tras la migracion",
         "Tras la migracion del sistema ya no aparece la opcion de menu de facturacion recurrente que se usaba a diario."),
        ("Instalacion bloqueada por permisos en el equipo nuevo",
         "En el equipo nuevo la instalacion del cliente corporativo se detiene por falta de permisos en la unidad C:."),
        ("El lector de codigos no reconoce los ficheros",
         "El lector de codigos de barras deja de reaccionar al abrir los ficheros de inventario generados la semana pasada."),
        ("Cliente de facturacion cierra al imprimir",
         "El cliente de facturacion se cierra de forma abrupta justo al enviar el documento a la impresora."),
        ("Version antigua del navegador bloquea la intranet",
         "La intranet muestra una pantalla de aviso de version no compatible porque el navegador no se actualiza solo."),
        ("Sincronizacion de agenda con movil detenida",
         "La agenda corporativa dejo de sincronizarse con el movil el lunes, sin cambios aparentes en la configuracion."),
    ],
    3: [  # Redes
        ("No puedo conectarme a la VPN desde casa",
         "Al intentar iniciar el cliente VPN desde el portatil corporativo, la conexion falla con un error de autenticacion tras varios intentos."),
        ("La Wi-Fi de la sala de reuniones se cae a menudo",
         "La red inalambrica de la sala de reuniones se desconecta varias veces durante cada sesion."),
        ("No se alcanza el servidor de archivos desde planta 3",
         "Desde los equipos de la planta 3 no se abre el servidor de archivos, pero el resto de plantas conecta con normalidad."),
        ("Conexion a internet intermitente en la zona de recepcion",
         "En recepcion la conexion a internet se corta cada pocos minutos y vuelve sola sin aviso previo."),
        ("El ordenador no obtiene direccion IP automatica",
         "Al enchufar el equipo a la red cableada no obtiene direccion IP y hay que configurarla a mano para conectar."),
        ("Acceso lento al portal compartido por VPN",
         "Abrir el portal tarda mas de un minuto solo cuando se accede desde fuera de la oficina mediante VPN."),
        ("Los equipos no ven la impresora de red",
         "Ningun equipo de la zona norte encuentra la impresora de red en la lista, aunque ayer aparecia con normalidad."),
        ("Puerto bloqueado para la aplicion de consulta",
         "La aplicion de consulta externa no conecta porque el puerto 8443 parece bloqueado en la pasarela."),
        ("Perdida de paquetes en la conexion con la oficina",
         "Durante las videollamadas con la sucursal se escuchan cortes y el informe de red muestra perdida de paquetes."),
        ("El punto de acceso de direccion no emite senal",
         "El punto de acceso montado en el almacen dejo de emitir senal desde el cambio de planta."),
    ],
    4: [  # Accesos
        ("Solicito acceso a la carpeta compartida de Nomina",
         "Necesito acceso de lectura a la carpeta compartida de Nomina para poder consultar los justificantes del mes en curso."),
        ("Alta de usuario para la nueva becaria de marketing",
         "Se ha incorporado una becaria al equipo de marketing y necesita cuenta de correo, acceso a la intranet y al compartido del departamento."),
        ("Sin permisos para validar anticipos",
         "Al intentar validar anticipos el sistema avisa de que mi perfil no tiene permisos suficientes para esa accion."),
        ("Cuenta bloqueada tras varios intentos fallidos",
         "Mi cuenta quedo bloqueada esta manana tras varios intentos fallidos de acceso y ahora no puedo iniciar sesion."),
        ("Baja de acceso para colaborador externo",
         "El colaborador externo dejo de trabajar con nosotros el viernes y hay que retirarle el acceso al portal de proyectos."),
        ("Permiso de solo lectura en el compartido de Calidad",
         "Necesito acceso de solo lectura al compartido de Calidad para revisar los informes de auditoria interna."),
        ("Acceso al modulo de contratacion para direccion",
         "La direccion necesita entrar al modulo de contratacion para revisar las propuestas antes del cierre del trimestre."),
        ("Necesito acceso a la base de datos de informes",
         "Para preparar el informe mensual necesito acceso de lectura a la base de datos de informes, sin permisos de escritura."),
        ("Cambiar el correo vinculado a mi usuario",
         "He cambiado de departamento y quiero recibir los avisos del sistema en el correo del nuevo equipo."),
        ("Restaurar acceso tras la baja por vacaciones",
         "Al volver de vacaciones el sistema no me deja entrar y me pide contactar con soporte para reactivar el perfil."),
    ],
    5: [  # Correo y Comunicaciones
        ("Outlook no sincroniza el correo desde ayer",
         "El cliente de Outlook se queda cargando indefinidamente al intentar sincronizar la bandeja de entrada desde ayer por la tarde."),
        ("No llegan los correos con adjuntos grandes",
         "Los mensajes con adjuntos de mas de 10 MB no llegan al destinatario y no veo ningun aviso de error."),
        ("Cuentas automaticas inundan la bandeja",
         "La bandeja de entrada recibe decenas de correos automaticos de inventario cada hora desde el lunes."),
        ("No puedo aceptar invitaciones de calendario",
         "Al abrir una invitacion de reunion el boton de aceptar aparece desactivado y la reunion no se anade a mi agenda."),
        ("Falta el historial de conversaciones en el cliente",
         "En el cliente de correo solo aparecen los mensajes del ultimo mes, el historial anterior no se carga."),
        ("Firma corporativa desaparece de los mensajes",
         "Desde esta semana los mensajes nuevos salen sin la firma corporativa aunque esta configurada en las opciones."),
        ("Notificaciones de chat duplicadas en el movil",
         "Cada mensaje de chat llega dos veces como notificacion en el movil del telefono corporativo."),
        ("Correo rebotado hacia destinatarios externos",
         "Los mensajes enviados a clientes externos rebotan con un aviso de politica de destinatarios."),
        ("Lista de distribucion del departamento incompleta",
         "La lista de distribucion de Contabilidad ya no incluye a las dos personas que se incorporaron este mes."),
        ("Grabacion de llamadas no disponible en la sala 2",
         "El boton de grabacion no aparece en la pantalla de la sala 2 aunque si funciona en las demas salas."),
    ],
}

SOLUCIONES = {
    1: "Se ha sustituido el componente defectuoso y el equipo vuelve a operar con normalidad",
    2: "Se ha reparado la instalacion y actualizado la aplicacion; el fallo ya no se reproduce",
    3: "Se ha renovado la configuracion de red y la conexion vuelve a establecerse sin errores",
    4: "Se han concedido los permisos solicitados y el acceso queda activo",
    5: "Se ha reconstruido el perfil de correo y la sincronizacion funciona de nuevo",
}

DIAGNOSTICOS = {
    1: "He revisado el equipo en su puesto de trabajo",
    2: "He revisado la aplicacion en el equipo del usuario",
    3: "He revisado la conexion y los logs de la pasarela",
    4: "He revisado los permisos del usuario en el directorio",
    5: "He revisado la cola del cliente de correo",
}

PREGUNTAS = {
    1: "Para avanzar necesito que me confirmes si el fallo ocurre tambien conectado a otro enchufe",
    2: "Para avanzar necesito que me confirmes la version exacta que aparece en el pie de la aplicacion",
    3: "Para avanzar necesito que me confirmes si el fallo ocurre tambien con cable y no solo por Wi-Fi",
    4: "Para avanzar necesito que me confirmes el usuario y el departamento exactos del solicitante",
    5: "Para avanzar necesito que me confirmes desde que fecha exacta falta el correo",
}

RESPUESTAS_USUARIO = {
    1: "Confirmado: con otro enchufe sigue igual, te paso la serie del equipo por si sirve",
    2: "Te confirmo la version que me pides: es la 8.4.2, la ultima que nos ofrece el fabricante",
    3: "Acabo de probar con cable y ocurre exactamente igual desde esta manana",
    4: "El usuario es de Contabilidad y el departamento exacto es Nomina, te lo confirmo",
    5: "El fallo empezo el lunes 5 de octubre, desde primera hora de la manana",
}


def sql(texto: str) -> str:
    return "'" + texto.replace("'", "''") + "'"


_POOLS: dict[int, list[tuple[str, str]]] = {}


def _tomar_incidencia(categoria: int) -> tuple[str, str]:
    """Titular y descripcion sin repetir mientras haya pool disponible."""
    pool = _POOLS.get(categoria)
    if not pool:
        pool = INCIDENCIAS[categoria][:]
        rng.shuffle(pool)
        _POOLS[categoria] = pool
    return pool.pop()


def ts(fecha: datetime) -> str:
    return "'" + fecha.strftime("%Y-%m-%d %H:%M:%S") + "'"


def en_horario(fecha: datetime) -> datetime:
    """Normaliza un datetime al horario de guardia del helpdesk (8:00-18:00).

    El servicio presta soporte continuo, asi que no se descartan fines de
    semana; solo se evitan tramos fuera de turno para que la cronologia sea
    creible.
    """
    if fecha.hour < 8:
        fecha = fecha.replace(hour=8, minute=rng.randint(0, 45))
    elif fecha.hour >= 18:
        fecha = fecha.replace(hour=rng.randint(8, 10), minute=rng.randint(0, 59))
    return fecha


def eleccion(pares):
    """pares: lista de (peso, valor)."""
    valores = [v for _, v in pares]
    pesos = [p for p, _ in pares]
    return rng.choices(valores, weights=pesos, k=1)[0]


def destino_fate(edad_dias: int) -> str:
    if edad_dias <= 2:
        return eleccion([(3, "abierto"), (2, "en_progreso"),
                         (1, "pendiente"), (1, "resuelto")])
    if edad_dias <= 7:
        return eleccion([(2, "en_progreso"), (1, "pendiente"),
                         (4, "resuelto"), (3, "cerrado")])
    return eleccion([(1, "en_progreso"), (1, "pendiente"),
                     (4, "resuelto"), (7, "cerrado")])


def construir_ticket(id_ticket: int) -> dict:
    categoria = rng.randint(1, 5)
    titulo, descripcion = _tomar_incidencia(categoria)

    # Fecha de creacion repartida entre septiembre y octubre
    edad_dias = eleccion([(6, rng.randint(0, 2)),        # esta semana
                          (7, rng.randint(3, 10)),       # 3-10 dias
                          (10, rng.randint(11, 38))])    # viejos
    fecha_creacion = en_horario(
        AHORA - timedelta(days=edad_dias,
                          hours=rng.randint(0, 8),
                          minutes=rng.randint(0, 59)))
    fecha_creacion = fecha_creacion.replace(second=0)

    prioridad = eleccion([(2, 1), (4, 2), (3, 3), (1, 4)])  # Baja/Media/Alta/Critica
    creador = eleccion([(5, 1), (4, 2), (1, 5)])
    tecnico = eleccion([(6, 3), (5, 4)])
    fate = destino_fate(edad_dias)

    eventos = [{"campo": "estado", "de": None, "a": "Abierto",
                "cuando": fecha_creacion, "quien": creador}]
    comentarios = []  # (usuario, texto, interno, cuando)

    t = fecha_creacion
    estado_actual = "Abierto"
    tecnico_asignado = None

    def asignar():
        nonlocal t, tecnico_asignado, estado_actual
        t = en_horario(t + timedelta(minutes=rng.randint(8, 200)))
        tecnico_asignado = tecnico
        eventos.append({"campo": "tecnico_asignado", "de": None,
                        "a": USUARIOS[tecnico], "cuando": t, "quien": tecnico})
        t = en_horario(t + timedelta(minutes=rng.randint(3, 40)))
        eventos.append({"campo": "estado", "de": "Abierto", "a": "En_Progreso",
                        "cuando": t, "quien": tecnico})
        estado_actual = "En_Progreso"
        comentarios.append((tecnico, DIAGNOSTICOS[categoria] + ", en curso.", t))
        if rng.random() < 0.25:
            otro = 4 if tecnico == 3 else 3
            comentarios.append((tecnico,
                                "Nota interna: causa conocida del catalogo interno, "
                                "revisar antes con " + USUARIOS[otro],
                                t + timedelta(minutes=5), True))

    def pedir_info():
        nonlocal t, estado_actual
        t = en_horario(t + timedelta(minutes=rng.randint(60, 400)))
        eventos.append({"campo": "estado", "de": "En_Progreso",
                        "a": "Pendiente_Usuario", "cuando": t, "quien": tecnico})
        estado_actual = "Pendiente_Usuario"
        comentarios.append((tecnico, PREGUNTAS[categoria] + ".", t))

    def responder():
        nonlocal t, estado_actual
        t = en_horario(t + timedelta(hours=rng.randint(2, 30),
                                     minutes=rng.randint(0, 59)))
        comentarios.append((creador, RESPUESTAS_USUARIO[categoria], t))
        eventos.append({"campo": "estado", "de": "Pendiente_Usuario",
                        "a": "En_Progreso", "cuando": t, "quien": tecnico})
        estado_actual = "En_Progreso"

    def resolver() -> datetime:
        nonlocal t, estado_actual
        previo = t
        t = en_horario(t + timedelta(minutes=rng.randint(25, 300)))
        if t > AHORA - timedelta(minutes=15):
            t = en_horario(AHORA - timedelta(minutes=rng.randint(15, 240)))
            if t <= previo:
                t = en_horario(previo + timedelta(minutes=rng.randint(20, 90)))
        comentarios.append((tecnico,
                            "Solucion aplicada: " + SOLUCIONES[categoria]
                            + ". Quedo a la espera de tu confirmacion.", t))
        eventos.append({"campo": "estado", "de": estado_actual, "a": "Resuelto",
                        "cuando": t, "quien": tecnico})
        estado_actual = "Resuelto"
        return t

    def cerrar(resolucion: datetime):
        nonlocal t, estado_actual
        previo = resolucion
        t = en_horario(resolucion + timedelta(hours=rng.randint(1, 36),
                                              minutes=rng.randint(0, 59)))
        if t > AHORA - timedelta(minutes=15):
            t = en_horario(AHORA - timedelta(minutes=rng.randint(15, 240)))
            if t <= previo:
                t = en_horario(previo + timedelta(minutes=rng.randint(20, 90)))
        comentarios.append((creador,
                            "Confirmado, todo funciona correctamente. Muchas gracias.",
                            t))
        eventos.append({"campo": "estado", "de": "Resuelto", "a": "Cerrado",
                        "cuando": t, "quien": creador})
        estado_actual = "Cerrado"

    fecha_cierre = None
    if fate == "abierto":
        pass  # solo creacion
    elif fate == "en_progreso":
        asignar()
        if rng.random() < 0.3:
            pedir_info()
    elif fate == "pendiente":
        asignar()
        pedir_info()
    elif fate == "resuelto":
        asignar()
        if rng.random() < 0.4:
            pedir_info()
            responder()
        fecha_cierre = resolver()
    else:  # cerrado
        asignar()
        if rng.random() < 0.4:
            pedir_info()
            responder()
        fecha_cierre = resolver()
        cerrar(fecha_cierre)

    # Escalada de prioridad ocasional (recalcula el SLA como hace el servicio)
    if prioridad in (1, 2) and rng.random() < 0.14 and eventos:
        nueva = prioridad + 1
        cuando = en_horario(fecha_creacion + timedelta(hours=rng.randint(1, 12)))
        quien = eleccion([(3, 3), (3, 4), (2, 5)])
        eventos.append({"campo": "prioridad", "de": nombre_prioridad(prioridad),
                        "a": nombre_prioridad(nueva), "cuando": cuando,
                        "quien": quien})
        prioridad = nueva

    limite = fecha_creacion + timedelta(hours=SLA_HORAS[prioridad])
    if limite < fecha_creacion:
        raise AssertionError("fecha_limite anterior a la creacion")

    _validar(eventos, comentarios, fecha_creacion, fecha_cierre)

    return {
        "id": id_ticket,
        "titulo": titulo,
        "descripcion": descripcion,
        "estado": list(ESTADOS.values()).index(estado_actual) + 1,
        "prioridad": prioridad,
        "creacion": fecha_creacion,
        "limite": limite,
        "cierre": fecha_cierre,
        "categoria": categoria,
        "creador": creador,
        "tecnico": tecnico_asignado,
        "eventos": sorted(eventos, key=lambda e: e["cuando"]),
        "comentarios": sorted(comentarios, key=lambda c: c[2]),
        "fate": fate,
    }


class Reintentar(Exception):
    """El ciclo de vida generado no cabe en el calendario: otro intento."""


COTA_ALTA = AHORA - timedelta(minutes=15)


def _validar(eventos, comentarios, creacion, cierre):
    """Rechaza ciclos de vida con eventos futuros o fuera de orden."""
    estados = [e["cuando"] for e in eventos if e["campo"] == "estado"]
    if any(estados[i] >= estados[i + 1] for i in range(len(estados) - 1)):
        raise Reintentar("transiciones de estado fuera de orden")
    if any(e["cuando"] > COTA_ALTA or e["cuando"] < creacion for e in eventos):
        raise Reintentar("evento fuera de rango")
    if any(c[2] > COTA_ALTA for c in comentarios):
        raise Reintentar("comentario futuro")
    if cierre is not None and cierre <= creacion:
        raise Reintentar("cierre anterior a la creacion")


def nombre_prioridad(id_prioridad: int) -> str:
    return {1: "Baja", 2: "Media", 3: "Alta", 4: "Critica"}[id_prioridad]


# ---------------------------------------------------------------------------
# Salida SQL
# ---------------------------------------------------------------------------

def generar(n: int = 48) -> str:
    tickets = []
    for id_ticket in range(1, n + 1):
        for intento in range(200):
            try:
                tickets.append(construir_ticket(id_ticket))
                break
            except Reintentar:
                continue
        else:
            raise RuntimeError(
                "no se pudo generar una vida valida para el ticket {}".format(id_ticket))

    lineas_tickets, lineas_hist, lineas_com, lineas_val, lineas_adj = [], [], [], [], []
    id_hist = id_com = id_val = id_adj = 1

    for tk in tickets:
        cierre = ts(tk["cierre"]) if tk["cierre"] else "NULL"
        tecnico = str(tk["tecnico"]) if tk["tecnico"] else "NULL"
        lineas_tickets.append(
            "({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})".format(
                tk["id"], sql(tk["titulo"]), sql(tk["descripcion"]),
                tk["estado"], tk["prioridad"], ts(tk["creacion"]),
                ts(tk["limite"]), cierre, tk["categoria"], tk["creador"],
                tecnico))

        for ev in tk["eventos"]:
            de = sql(ev["de"]) if ev["de"] else "NULL"
            lineas_hist.append(
                "({}, {}, {}, {}, {}, {}, {})".format(
                    id_hist, tk["id"], ev["quien"], sql(ev["campo"]),
                    de, sql(ev["a"]), ts(ev["cuando"])))
            id_hist += 1

        for usuario, texto, cuando, *interno in tk["comentarios"]:
            es_interno = bool(interno and interno[0])
            lineas_com.append(
                "({}, {}, {}, {}, {}, {})".format(
                    id_com, tk["id"], usuario, sql(texto),
                    "TRUE" if es_interno else "FALSE", ts(cuando)))
            id_com += 1

        if tk["fate"] == "cerrado":
            puntuacion = eleccion([(1, 3), (4, 4), (5, 5)])
            texto = {3: "Resuelto, aunque algo lento para mi gusto.",
                     4: "Rapido y sin incidencias, gracias.",
                     5: "Resuelto a la primera, muy buen trabajo."}[puntuacion]
            lineas_val.append(
                "({}, {}, {}, {}, {})".format(
                    id_val, tk["id"], puntuacion, sql(texto),
                    ts(tk["eventos"][-1]["cuando"] + timedelta(minutes=5))))
            id_val += 1

        if tk["id"] % 7 == 0:
            adjuntos = {1: ("fallo-fuente.png", 340),
                        2: ("log-aplicacion.txt", 24),
                        3: ("diagnostico-red.txt", 18),
                        4: ("permisos-directorio.png", 210),
                        5: ("error-bandeja.png", 265)}[tk["categoria"]]
            lineas_adj.append(
                "({}, {}, {}, {}, {}, {})".format(
                    id_adj, tk["id"], sql(adjuntos[0]),
                    sql("/adjuntos/ticket_{}/{}".format(tk["id"], adjuntos[0])),
                    adjuntos[1], ts(tk["creacion"] + timedelta(minutes=12))))
            id_adj += 1

    def bloque(nombre: str, columnas: str, filas: list[str]) -> str:
        cuerpo = ",\n".join(filas)
        return "INSERT INTO {} ({}) VALUES\n{};".format(nombre, columnas, cuerpo)

    partes = [
        bloque("Tickets",
               "id_ticket, titulo, descripcion, id_estado, id_prioridad, "
               "fecha_creacion, fecha_limite, fecha_cierre, id_categoria, "
               "usuario_creador, tecnico_asignado",
               lineas_tickets),
        bloque("Historial_Ticket",
               "id_historico, id_ticket, id_usuario, campo, valor_anterior, "
               "valor_nuevo, fecha_cambio",
               lineas_hist),
        bloque("Comentarios_Ticket",
               "id_comentario, id_ticket, id_usuario, comentario, es_interno, "
               "fecha_comentario",
               lineas_com),
    ]
    if lineas_val:
        partes.append(bloque("Valoraciones",
                             "id_valoracion, id_ticket, puntuacion, comentario, "
                             "fecha_valoracion",
                             lineas_val))
    if lineas_adj:
        partes.append(bloque("Adjuntos_Ticket",
                             "id_adjunto, id_ticket, nombre_archivo, ruta, "
                             "tamano_kb, fecha_subida",
                             lineas_adj))

    return "\n\n".join(partes) + "\n"


def main() -> None:
    texto = SCHEMA.read_text(encoding="utf-8")
    inicio = texto.index(MARCADOR_INICIO) + len(MARCADOR_INICIO)
    fin = texto.index(MARCADOR_FIN)
    nuevo = generar()
    SCHEMA.write_text(
        texto[:inicio] + "\n-- (bloque regenerado por tools/seed_generator.py)\n\n"
        + nuevo + texto[fin:],
        encoding="utf-8")
    print("schema.sql actualizado con el bloque generado.")


if __name__ == "__main__":
    main()
