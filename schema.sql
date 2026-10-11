-- ==========================================================================
-- Sistema de Helpdesk — esquema relacional completo
-- Motor: MySQL 8.x (la semilla es compatible con H2 en modo MySQL, que es lo
--        que usan los tests JUnit para ejecutarse sin servidor).
--
-- Diseño:
--   * Roles, prioridades y estados son tablas lookup con FK (nada de CHECK
--     embebidos): permiten SLA por prioridad y validar el ciclo de vida.
--   * Transiciones_Estado codifica en base de datos qué cambios de estado son
--     legales; la capa de servicios los consulta antes de aplicarlos.
--   * Historial_Ticket registra cada modificacion relevante de un ticket
--     (campo, valor anterior, valor nuevo, autor y fecha): trazabilidad total.
--   * Cada ticket guarda fecha_limite = fecha_creacion + sla_horas de su
--     prioridad, lo que alimenta la vista de SLA incumplido.
--
-- Credenciales de la semilla: password de todos los usuarios -> royaltown2026
-- (hashes bcrypt $2a$10$ generados con la libreria bcrypt de Python).
-- ==========================================================================

-- ---------------------------------------------------------------------------
-- Tablas lookup
-- ---------------------------------------------------------------------------

CREATE TABLE Roles (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE Prioridades (
    id_prioridad INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(15) NOT NULL UNIQUE,
    orden INT NOT NULL UNIQUE,
    sla_horas INT NOT NULL CHECK (sla_horas > 0)
);

CREATE TABLE Estados (
    id_estado INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(25) NOT NULL UNIQUE,
    orden INT NOT NULL UNIQUE,
    es_final BOOLEAN NOT NULL DEFAULT FALSE
);

-- ---------------------------------------------------------------------------
-- Entidades principales
-- ---------------------------------------------------------------------------

CREATE TABLE Usuarios (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    id_rol INT NOT NULL,
    departamento VARCHAR(80),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_alta TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuarios_rol FOREIGN KEY (id_rol)
        REFERENCES Roles (id_rol)
        ON DELETE RESTRICT
);

CREATE TABLE Categorias (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE,
    descripcion VARCHAR(255)
);

CREATE TABLE Tickets (
    id_ticket INT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT NOT NULL,
    id_estado INT NOT NULL,
    id_prioridad INT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_limite TIMESTAMP NOT NULL,
    fecha_cierre TIMESTAMP NULL,
    id_categoria INT NOT NULL,
    usuario_creador INT NOT NULL,
    tecnico_asignado INT NULL,
    CONSTRAINT chk_tickets_cierre CHECK (fecha_cierre IS NULL OR fecha_cierre >= fecha_creacion),
    CONSTRAINT chk_tickets_limite CHECK (fecha_limite >= fecha_creacion),
    CONSTRAINT fk_tickets_estado FOREIGN KEY (id_estado)
        REFERENCES Estados (id_estado)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_prioridad FOREIGN KEY (id_prioridad)
        REFERENCES Prioridades (id_prioridad)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_categoria FOREIGN KEY (id_categoria)
        REFERENCES Categorias (id_categoria)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_usuario_creador FOREIGN KEY (usuario_creador)
        REFERENCES Usuarios (id_usuario)
        ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_tecnico_asignado FOREIGN KEY (tecnico_asignado)
        REFERENCES Usuarios (id_usuario)
        ON DELETE SET NULL
);

-- ---------------------------------------------------------------------------
-- Ciclo de vida: transiciones legales y trazabilidad
-- ---------------------------------------------------------------------------

CREATE TABLE Transiciones_Estado (
    id_transicion INT AUTO_INCREMENT PRIMARY KEY,
    id_estado_origen INT NOT NULL,
    id_estado_destino INT NOT NULL,
    CONSTRAINT chk_transicion_no_ciclo CHECK (id_estado_origen <> id_estado_destino),
    CONSTRAINT uq_transicion UNIQUE (id_estado_origen, id_estado_destino),
    CONSTRAINT fk_transicion_origen FOREIGN KEY (id_estado_origen)
        REFERENCES Estados (id_estado)
        ON DELETE CASCADE,
    CONSTRAINT fk_transicion_destino FOREIGN KEY (id_estado_destino)
        REFERENCES Estados (id_estado)
        ON DELETE CASCADE
);

CREATE TABLE Historial_Ticket (
    id_historico INT AUTO_INCREMENT PRIMARY KEY,
    id_ticket INT NOT NULL,
    id_usuario INT NOT NULL,
    campo VARCHAR(30) NOT NULL,
    valor_anterior VARCHAR(255) NULL,
    valor_nuevo VARCHAR(255) NULL,
    fecha_cambio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historial_ticket FOREIGN KEY (id_ticket)
        REFERENCES Tickets (id_ticket)
        ON DELETE CASCADE,
    CONSTRAINT fk_historial_usuario FOREIGN KEY (id_usuario)
        REFERENCES Usuarios (id_usuario)
        ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------------
-- Hilo de conversacion y extras
-- ---------------------------------------------------------------------------

CREATE TABLE Comentarios_Ticket (
    id_comentario INT AUTO_INCREMENT PRIMARY KEY,
    id_ticket INT NOT NULL,
    id_usuario INT NOT NULL,
    comentario TEXT NOT NULL,
    es_interno BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_comentario TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comentarios_ticket FOREIGN KEY (id_ticket)
        REFERENCES Tickets (id_ticket)
        ON DELETE CASCADE,
    CONSTRAINT fk_comentarios_usuario FOREIGN KEY (id_usuario)
        REFERENCES Usuarios (id_usuario)
        ON DELETE RESTRICT
);

CREATE TABLE Adjuntos_Ticket (
    id_adjunto INT AUTO_INCREMENT PRIMARY KEY,
    id_ticket INT NOT NULL,
    nombre_archivo VARCHAR(180) NOT NULL,
    ruta VARCHAR(255) NOT NULL,
    tamano_kb INT NOT NULL CHECK (tamano_kb > 0),
    fecha_subida TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adjuntos_ticket FOREIGN KEY (id_ticket)
        REFERENCES Tickets (id_ticket)
        ON DELETE CASCADE
);

CREATE TABLE Valoraciones (
    id_valoracion INT AUTO_INCREMENT PRIMARY KEY,
    id_ticket INT NOT NULL UNIQUE,
    puntuacion TINYINT NOT NULL CHECK (puntuacion BETWEEN 1 AND 5),
    comentario VARCHAR(255) NULL,
    fecha_valoracion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_valoraciones_ticket FOREIGN KEY (id_ticket)
        REFERENCES Tickets (id_ticket)
        ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Indices sobre las busquedas frecuentes
-- ---------------------------------------------------------------------------

CREATE INDEX idx_tickets_estado_prioridad ON Tickets (id_estado, id_prioridad);
CREATE INDEX idx_tickets_creador ON Tickets (usuario_creador);
CREATE INDEX idx_tickets_tecnico ON Tickets (tecnico_asignado);
CREATE INDEX idx_tickets_fecha_creacion ON Tickets (fecha_creacion);
CREATE INDEX idx_tickets_fecha_limite ON Tickets (fecha_limite);
CREATE INDEX idx_comentarios_ticket ON Comentarios_Ticket (id_ticket);
CREATE INDEX idx_historial_ticket ON Historial_Ticket (id_ticket);

-- ---------------------------------------------------------------------------
-- Vistas de consulta / dashboard
-- ---------------------------------------------------------------------------

-- Cola de trabajo: tickets todavia sin resolver (excluye Resuelto, que ya
-- solo espera la confirmacion del usuario, y Cerrado).
CREATE OR REPLACE VIEW vw_tickets_abiertos AS
SELECT t.id_ticket,
       t.titulo,
       c.nombre AS categoria,
       p.nombre AS prioridad,
       e.nombre AS estado,
       u.nombre_completo AS tecnico,
       t.fecha_creacion,
       t.fecha_limite
FROM Tickets t
JOIN Estados e   ON e.id_estado = t.id_estado
JOIN Prioridades p ON p.id_prioridad = t.id_prioridad
JOIN Categorias c  ON c.id_categoria = t.id_categoria
LEFT JOIN Usuarios u ON u.id_usuario = t.tecnico_asignado
WHERE e.nombre NOT IN ('Resuelto', 'Cerrado');

-- SLA en riesgo: sin resolver y con fecha_limite ya pasada.
CREATE OR REPLACE VIEW vw_sla_incumplido AS
SELECT t.id_ticket,
       t.titulo,
       p.nombre AS prioridad,
       p.sla_horas,
       t.fecha_creacion,
       t.fecha_limite,
       u.nombre_completo AS tecnico
FROM Tickets t
JOIN Prioridades p ON p.id_prioridad = t.id_prioridad
LEFT JOIN Usuarios u ON u.id_usuario = t.tecnico_asignado
JOIN Estados e ON e.id_estado = t.id_estado
WHERE e.nombre NOT IN ('Resuelto', 'Cerrado')
  AND t.fecha_limite < CURRENT_TIMESTAMP;

-- Rendimiento por tecnico sobre tickets cerrados o resueltos.
CREATE OR REPLACE VIEW vw_resolucion_por_tecnico AS
SELECT u.id_usuario,
       u.nombre_completo,
       COUNT(*) AS tickets_resueltos,
       ROUND(AVG(TIMESTAMPDIFF(MINUTE, t.fecha_creacion, t.fecha_cierre))) AS minutos_medios
FROM Tickets t
JOIN Usuarios u ON u.id_usuario = t.tecnico_asignado
WHERE t.fecha_cierre IS NOT NULL
GROUP BY u.id_usuario, u.nombre_completo;

-- Reparto de tickets por estado, en orden de ciclo de vida.
CREATE OR REPLACE VIEW vw_tickets_por_estado AS
SELECT e.id_estado,
       e.nombre,
       e.orden,
       COUNT(t.id_ticket) AS total
FROM Estados e
LEFT JOIN Tickets t ON t.id_estado = e.id_estado
GROUP BY e.id_estado, e.nombre, e.orden
ORDER BY e.orden;

-- ---------------------------------------------------------------------------
-- Semilla: lookups, usuarios y categorias
-- ---------------------------------------------------------------------------

INSERT INTO Roles (nombre) VALUES
('empleado'),
('tecnico'),
('administrador');

INSERT INTO Prioridades (nombre, orden, sla_horas) VALUES
('Baja', 1, 72),
('Media', 2, 24),
('Alta', 3, 8),
('Critica', 4, 4);

INSERT INTO Estados (nombre, orden, es_final) VALUES
('Abierto', 1, FALSE),
('En_Progreso', 2, FALSE),
('Pendiente_Usuario', 3, FALSE),
('Resuelto', 4, FALSE),
('Cerrado', 5, TRUE);

INSERT INTO Transiciones_Estado (id_estado_origen, id_estado_destino) VALUES
(1, 2),  -- Abierto -> En_Progreso (un tecnico se hace cargo)
(1, 3),  -- Abierto -> Pendiente_Usuario (se pide aclaracion al reportero)
(2, 1),  -- En_Progreso -> Abierto (se devuelve a la cola)
(2, 3),  -- En_Progreso -> Pendiente_Usuario (falta informacion del usuario)
(2, 4),  -- En_Progreso -> Resuelto (la incidencia queda solucionada)
(3, 2),  -- Pendiente_Usuario -> En_Progreso (el usuario responde)
(3, 1),  -- Pendiente_Usuario -> Abierto (se reinicia la indagacion)
(4, 5),  -- Resuelto -> Cerrado (el usuario confirma la solucion)
(4, 2),  -- Resuelto -> En_Progreso (reapertura: no era la solucion)
(5, 2);  -- Cerrado -> En_Progreso (reapertura tardia por el tecnico)

INSERT INTO Usuarios (nombre_completo, email, password_hash, id_rol, departamento) VALUES
('Marta Sanchez Lopez', 'marta.sanchez@royaltown.local', '$2a$10$BpfUaQEx3zWcRVmZR4gD5eq3GOj52CCIA..8BGwXOirzUT8Y2lNQi', 1, 'Contabilidad'),
('Javier Torres Ruiz', 'javier.torres@royaltown.local', '$2a$10$Dqxgc9NXAIwFHMgdfNx7WuTuZasX00X6FvKH0r9PaW6B5UiVPWfAq', 1, 'Recursos Humanos'),
('Laura Gomez Martin', 'laura.gomez@royaltown.local', '$2a$10$s2rv4egPTOI6osu.WSRb5.bTo9tYj7l5W9SJxNs03X0hXmkJa/2WG', 2, 'Soporte IT'),
('Diego Fernandez Ibanez', 'diego.fernandez@royaltown.local', '$2a$10$Dy1rJ.8Wa.LcEZ7YyAiWauDyVbwuy/70TfZ4EZvGQZjfU3RemWZNS', 2, 'Soporte IT'),
('Alejandro Ortiz Vega', 'alejandro.ortiz@royaltown.local', '$2a$10$YRKqVsLpJxjTZhoi7.QPp.aaNqw8hYxV2vReuDpEpPDdV93vOpxeC', 3, 'Sistemas');

INSERT INTO Categorias (nombre, descripcion) VALUES
('Hardware', 'Incidencias relacionadas con equipos fisicos, perifericos y dispositivos de usuario.'),
('Software', 'Instalacion, actualizacion o fallos de aplicaciones y sistemas operativos.'),
('Redes', 'Conectividad, VPN, Wi-Fi y acceso a recursos de red interna.'),
('Accesos', 'Altas, bajas y permisos de usuario en sistemas y aplicaciones corporativas.'),
('Correo y Comunicaciones', 'Incidencias de correo electronico, calendario y herramientas de mensajeria.');

-- >>> DATOS_GENERADOS_INICIO <<<
-- (bloque regenerado por tools/seed_generator.py)

INSERT INTO Tickets (id_ticket, titulo, descripcion, id_estado, id_prioridad, fecha_creacion, fecha_limite, fecha_cierre, id_categoria, usuario_creador, tecnico_asignado) VALUES
(1, 'Permiso de solo lectura en el compartido de Calidad', 'Necesito acceso de solo lectura al compartido de Calidad para revisar los informes de auditoria interna.', 5, 4, '2026-09-08 11:47:00', '2026-09-08 15:47:00', '2026-09-08 14:32:00', 4, 2, 4),
(2, 'Firma corporativa desaparece de los mensajes', 'Desde esta semana los mensajes nuevos salen sin la firma corporativa aunque esta configurada en las opciones.', 3, 3, '2026-09-11 08:05:00', '2026-09-11 16:05:00', NULL, 5, 1, 4),
(3, 'El ventilador del equipo hace mucho ruido', 'Desde hace unos dias el ventilador del equipo emite un ruido fuerte y continuo que resulta molesto en la oficina.', 5, 2, '2026-10-01 09:12:00', '2026-10-02 09:12:00', '2026-10-02 17:39:00', 1, 2, 3),
(4, 'Sincronizacion de agenda con movil detenida', 'La agenda corporativa dejo de sincronizarse con el movil el lunes, sin cambios aparentes en la configuracion.', 4, 2, '2026-09-15 10:37:00', '2026-09-16 10:37:00', '2026-09-15 17:59:00', 2, 1, 3),
(5, 'Monitor con lineas verticales despues de un golpe', 'El monitor presenta tres lineas verticales permanentes desde que golpeo accidentalmente la pantalla al mover la mesa.', 5, 1, '2026-10-07 11:47:00', '2026-10-10 11:47:00', '2026-10-07 17:10:00', 1, 1, 3),
(6, 'Restaurar acceso tras la baja por vacaciones', 'Al volver de vacaciones el sistema no me deja entrar y me pide contactar con soporte para reactivar el perfil.', 2, 2, '2026-10-10 11:22:00', '2026-10-11 11:22:00', NULL, 4, 2, 4),
(7, 'Instalacion bloqueada por permisos en el equipo nuevo', 'En el equipo nuevo la instalacion del cliente corporativo se detiene por falta de permisos en la unidad C:.', 4, 3, '2026-10-10 09:07:00', '2026-10-10 17:07:00', '2026-10-11 12:41:00', 2, 1, 4),
(8, 'El programa de contabilidad pide activarse cada dia', 'Cada manana al abrir el programa de contabilidad vuelve a pedir la clave de activacion aunque se guardo ayer.', 1, 3, '2026-10-11 14:37:00', '2026-10-11 22:37:00', NULL, 2, 5, NULL),
(9, 'Cliente de facturacion cierra al imprimir', 'El cliente de facturacion se cierra de forma abrupta justo al enviar el documento a la impresora.', 5, 2, '2026-10-02 11:11:00', '2026-10-03 11:11:00', '2026-10-03 16:05:00', 2, 2, 4),
(10, 'Sin permisos para validar anticipos', 'Al intentar validar anticipos el sistema avisa de que mi perfil no tiene permisos suficientes para esa accion.', 3, 1, '2026-10-10 11:22:00', '2026-10-13 11:22:00', NULL, 4, 5, 3),
(11, 'No llegan los correos con adjuntos grandes', 'Los mensajes con adjuntos de mas de 10 MB no llegan al destinatario y no veo ningun aviso de error.', 4, 4, '2026-09-22 09:17:00', '2026-09-22 13:17:00', '2026-09-22 12:40:00', 5, 1, 4),
(12, 'Version antigua del navegador bloquea la intranet', 'La intranet muestra una pantalla de aviso de version no compatible porque el navegador no se actualiza solo.', 3, 1, '2026-10-09 08:23:00', '2026-10-12 08:23:00', NULL, 2, 1, 4),
(13, 'Errores al exportar informes a Excel', 'Al exportar cualquier informe a Excel el archivo se genera vacio o con las columnas desalineadas.', 4, 2, '2026-10-04 12:00:00', '2026-10-05 12:00:00', '2026-10-04 13:52:00', 2, 1, 3),
(14, 'Solicito acceso a la carpeta compartida de Nomina', 'Necesito acceso de lectura a la carpeta compartida de Nomina para poder consultar los justificantes del mes en curso.', 4, 2, '2026-10-09 11:25:00', '2026-10-10 11:25:00', '2026-10-09 16:01:00', 4, 1, 4),
(15, 'Portatil que se apaga solo al desconectar el cargador', 'El portatil se apaga instantaneamente al retirar el cargador aunque la bateria indique un 70 por ciento.', 5, 3, '2026-09-03 11:40:00', '2026-09-03 19:40:00', '2026-09-03 15:00:00', 1, 1, 4),
(16, 'Cambiar el correo vinculado a mi usuario', 'He cambiado de departamento y quiero recibir los avisos del sistema en el correo del nuevo equipo.', 1, 4, '2026-10-11 13:00:00', '2026-10-11 17:00:00', NULL, 4, 2, NULL),
(17, 'Perdida de paquetes en la conexion con la oficina', 'Durante las videollamadas con la sucursal se escuchan cortes y el informe de red muestra perdida de paquetes.', 4, 2, '2026-10-01 08:24:00', '2026-10-02 08:24:00', '2026-10-01 14:23:00', 3, 1, 4),
(18, 'Alta de usuario para la nueva becaria de marketing', 'Se ha incorporado una becaria al equipo de marketing y necesita cuenta de correo, acceso a la intranet y al compartido del departamento.', 1, 3, '2026-10-09 14:51:00', '2026-10-09 22:51:00', NULL, 4, 1, NULL),
(19, 'Falta una opcion de menu tras la migracion', 'Tras la migracion del sistema ya no aparece la opcion de menu de facturacion recurrente que se usaba a diario.', 2, 4, '2026-10-07 10:14:00', '2026-10-07 14:14:00', NULL, 2, 5, 4),
(20, 'No se abre la aplicacion de gestion tras la actualizacion', 'Despues de la ultima actualizacion la aplicacion de gestion cierra inmediatamente sin mostrar ningun mensaje de error.', 1, 1, '2026-10-09 10:25:00', '2026-10-12 10:25:00', NULL, 2, 1, NULL),
(21, 'Los equipos no ven la impresora de red', 'Ningun equipo de la zona norte encuentra la impresora de red en la lista, aunque ayer aparecia con normalidad.', 5, 3, '2026-09-12 10:32:00', '2026-09-12 18:32:00', '2026-09-12 17:06:00', 3, 5, 3),
(22, 'Outlook no sincroniza el correo desde ayer', 'El cliente de Outlook se queda cargando indefinidamente al intentar sincronizar la bandeja de entrada desde ayer por la tarde.', 4, 4, '2026-09-17 12:03:00', '2026-09-17 16:03:00', '2026-09-18 11:35:00', 5, 2, 4),
(23, 'Baja de acceso para colaborador externo', 'El colaborador externo dejo de trabajar con nosotros el viernes y hay que retirarle el acceso al portal de proyectos.', 5, 2, '2026-09-17 09:32:00', '2026-09-18 09:32:00', '2026-09-17 16:55:00', 4, 5, 4),
(24, 'Altavoz USB sin sonido en reuniones', 'El altavoz USB del salon de actos no emite sonido en las reuniones, aunque el sistema lo reconoce como dispositivo.', 4, 3, '2026-10-09 08:03:00', '2026-10-09 16:03:00', '2026-10-09 09:34:00', 1, 1, 4),
(25, 'Notificaciones de chat duplicadas en el movil', 'Cada mensaje de chat llega dos veces como notificacion en el movil del telefono corporativo.', 5, 1, '2026-09-08 14:26:00', '2026-09-11 14:26:00', '2026-09-08 17:46:00', 5, 1, 4),
(26, 'Outlook no sincroniza el correo desde ayer', 'El cliente de Outlook se queda cargando indefinidamente al intentar sincronizar la bandeja de entrada desde ayer por la tarde.', 3, 4, '2026-10-11 11:16:00', '2026-10-11 15:16:00', NULL, 5, 1, 4),
(27, 'Cuenta bloqueada tras varios intentos fallidos', 'Mi cuenta quedo bloqueada esta manana tras varios intentos fallidos de acceso y ahora no puedo iniciar sesion.', 2, 3, '2026-10-09 10:15:00', '2026-10-09 18:15:00', NULL, 4, 1, 4),
(28, 'Sincronizacion de agenda con movil detenida', 'La agenda corporativa dejo de sincronizarse con el movil el lunes, sin cambios aparentes en la configuracion.', 1, 3, '2026-10-09 11:04:00', '2026-10-09 19:04:00', NULL, 2, 1, NULL),
(29, 'Acceso lento al portal compartido por VPN', 'Abrir el portal tarda mas de un minuto solo cuando se accede desde fuera de la oficina mediante VPN.', 4, 2, '2026-09-11 11:56:00', '2026-09-12 11:56:00', '2026-09-12 08:51:00', 3, 2, 4),
(30, 'Raton inalambrico que se desconecta cada pocos minutos', 'El raton pierde la conexion de forma intermitente y hay que volver a emparejarlo cada pocos minutos.', 2, 3, '2026-10-10 08:38:00', '2026-10-10 16:38:00', NULL, 1, 1, 3),
(31, 'Falta el historial de conversaciones en el cliente', 'En el cliente de correo solo aparecen los mensajes del ultimo mes, el historial anterior no se carga.', 5, 3, '2026-10-01 08:28:00', '2026-10-01 16:28:00', '2026-10-01 11:15:00', 5, 1, 3),
(32, 'No se alcanza el servidor de archivos desde planta 3', 'Desde los equipos de la planta 3 no se abre el servidor de archivos, pero el resto de plantas conecta con normalidad.', 2, 1, '2026-10-10 14:52:00', '2026-10-13 14:52:00', NULL, 3, 1, 3),
(33, 'Escaner de la sala de juntas no alimenta papel', 'El escaner de la sala de juntas enciende pero no toma papel al intentar digitalizar documentos.', 4, 3, '2026-10-08 09:14:00', '2026-10-08 17:14:00', '2026-10-08 15:32:00', 1, 2, 3),
(34, 'La impresora de planta 2 no responde', 'La impresora compartida de la planta 2 aparece como desconectada para todos los usuarios de esa zona desde esta manana.', 4, 3, '2026-09-29 10:34:00', '2026-09-29 18:34:00', '2026-09-29 16:07:00', 1, 1, 4),
(35, 'Puerto bloqueado para la aplicion de consulta', 'La aplicion de consulta externa no conecta porque el puerto 8443 parece bloqueado en la pasarela.', 4, 3, '2026-09-09 12:49:00', '2026-09-09 20:49:00', '2026-09-10 12:09:00', 3, 2, 3),
(36, 'Falta una opcion de menu tras la migracion', 'Tras la migracion del sistema ya no aparece la opcion de menu de facturacion recurrente que se usaba a diario.', 5, 3, '2026-10-01 10:10:00', '2026-10-01 18:10:00', '2026-10-01 15:35:00', 2, 1, 3),
(37, 'Grabacion de llamadas no disponible en la sala 2', 'El boton de grabacion no aparece en la pantalla de la sala 2 aunque si funciona en las demas salas.', 1, 2, '2026-10-10 14:36:00', '2026-10-11 14:36:00', NULL, 5, 2, NULL),
(38, 'No puedo conectarme a la VPN desde casa', 'Al intentar iniciar el cliente VPN desde el portatil corporativo, la conexion falla con un error de autenticacion tras varios intentos.', 3, 2, '2026-10-09 08:05:00', '2026-10-10 08:05:00', NULL, 3, 2, 3),
(39, 'El ordenador no obtiene direccion IP automatica', 'Al enchufar el equipo a la red cableada no obtiene direccion IP y hay que configurarla a mano para conectar.', 1, 2, '2026-10-09 12:37:00', '2026-10-10 12:37:00', NULL, 3, 1, NULL),
(40, 'Firma corporativa desaparece de los mensajes', 'Desde esta semana los mensajes nuevos salen sin la firma corporativa aunque esta configurada en las opciones.', 1, 4, '2026-10-11 11:40:00', '2026-10-11 15:40:00', NULL, 5, 2, NULL),
(41, 'Portatil que se apaga solo al desconectar el cargador', 'El portatil se apaga instantaneamente al retirar el cargador aunque la bateria indique un 70 por ciento.', 2, 1, '2026-09-18 08:40:00', '2026-09-21 08:40:00', NULL, 1, 2, 3),
(42, 'Conexion a internet intermitente en la zona de recepcion', 'En recepcion la conexion a internet se corta cada pocos minutos y vuelve sola sin aviso previo.', 1, 3, '2026-10-11 16:17:00', '2026-10-12 00:17:00', NULL, 3, 2, NULL),
(43, 'Notificaciones de chat duplicadas en el movil', 'Cada mensaje de chat llega dos veces como notificacion en el movil del telefono corporativo.', 5, 2, '2026-09-16 11:59:00', '2026-09-17 11:59:00', '2026-09-17 10:06:00', 5, 2, 3),
(44, 'Alta de usuario para la nueva becaria de marketing', 'Se ha incorporado una becaria al equipo de marketing y necesita cuenta de correo, acceso a la intranet y al compartido del departamento.', 5, 2, '2026-10-01 09:28:00', '2026-10-02 09:28:00', '2026-10-01 15:00:00', 4, 1, 3),
(45, 'La Wi-Fi de la sala de reuniones se cae a menudo', 'La red inalambrica de la sala de reuniones se desconecta varias veces durante cada sesion.', 2, 2, '2026-10-11 09:16:00', '2026-10-12 09:16:00', NULL, 3, 1, 3),
(46, 'Correo rebotado hacia destinatarios externos', 'Los mensajes enviados a clientes externos rebotan con un aviso de politica de destinatarios.', 3, 3, '2026-10-09 12:35:00', '2026-10-09 20:35:00', NULL, 5, 2, 3),
(47, 'No puedo aceptar invitaciones de calendario', 'Al abrir una invitacion de reunion el boton de aceptar aparece desactivado y la reunion no se anade a mi agenda.', 2, 3, '2026-09-21 13:19:00', '2026-09-21 21:19:00', NULL, 5, 1, 3),
(48, 'El lector de codigos no reconoce los ficheros', 'El lector de codigos de barras deja de reaccionar al abrir los ficheros de inventario generados la semana pasada.', 2, 2, '2026-10-11 15:25:00', '2026-10-12 15:25:00', NULL, 2, 1, 3);

INSERT INTO Historial_Ticket (id_historico, id_ticket, id_usuario, campo, valor_anterior, valor_nuevo, fecha_cambio) VALUES
(1, 1, 2, 'estado', NULL, 'Abierto', '2026-09-08 11:47:00'),
(2, 1, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-08 12:01:00'),
(3, 1, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-08 12:04:00'),
(4, 1, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-08 14:32:00'),
(5, 1, 2, 'estado', 'Resuelto', 'Cerrado', '2026-09-09 08:23:00'),
(6, 2, 1, 'estado', NULL, 'Abierto', '2026-09-11 08:05:00'),
(7, 2, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-11 08:52:00'),
(8, 2, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-11 09:16:00'),
(9, 2, 4, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-09-11 13:25:00'),
(10, 3, 2, 'estado', NULL, 'Abierto', '2026-10-01 09:12:00'),
(11, 3, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-01 11:12:00'),
(12, 3, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-01 11:35:00'),
(13, 3, 3, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-01 17:41:00'),
(14, 3, 3, 'estado', 'Pendiente_Usuario', 'En_Progreso', '2026-10-02 15:27:00'),
(15, 3, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-10-02 17:39:00'),
(16, 3, 2, 'estado', 'Resuelto', 'Cerrado', '2026-10-03 14:30:00'),
(17, 4, 1, 'estado', NULL, 'Abierto', '2026-09-15 10:37:00'),
(18, 4, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-09-15 12:40:00'),
(19, 4, 3, 'estado', 'Abierto', 'En_Progreso', '2026-09-15 13:09:00'),
(20, 4, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-09-15 17:59:00'),
(21, 5, 1, 'estado', NULL, 'Abierto', '2026-10-07 11:47:00'),
(22, 5, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-07 13:58:00'),
(23, 5, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-07 14:15:00'),
(24, 5, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-10-07 17:10:00'),
(25, 5, 1, 'estado', 'Resuelto', 'Cerrado', '2026-10-08 15:38:00'),
(26, 6, 2, 'estado', NULL, 'Abierto', '2026-10-10 11:22:00'),
(27, 6, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-10 12:40:00'),
(28, 6, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-10 13:10:00'),
(29, 7, 1, 'estado', NULL, 'Abierto', '2026-10-10 09:07:00'),
(30, 7, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-10 10:34:00'),
(31, 7, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-10 11:11:00'),
(32, 7, 5, 'prioridad', 'Media', 'Alta', '2026-10-10 15:07:00'),
(33, 7, 4, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-10 17:27:00'),
(34, 7, 4, 'estado', 'Pendiente_Usuario', 'En_Progreso', '2026-10-11 08:26:00'),
(35, 7, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-10-11 12:41:00'),
(36, 8, 5, 'estado', NULL, 'Abierto', '2026-10-11 14:37:00'),
(37, 9, 2, 'estado', NULL, 'Abierto', '2026-10-02 11:11:00'),
(38, 9, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-02 11:53:00'),
(39, 9, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-02 12:01:00'),
(40, 9, 4, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-02 13:18:00'),
(41, 9, 4, 'estado', 'Pendiente_Usuario', 'En_Progreso', '2026-10-03 13:23:00'),
(42, 9, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-10-03 16:05:00'),
(43, 9, 2, 'estado', 'Resuelto', 'Cerrado', '2026-10-04 14:27:00'),
(44, 10, 5, 'estado', NULL, 'Abierto', '2026-10-10 11:22:00'),
(45, 10, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-10 13:09:00'),
(46, 10, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-10 13:13:00'),
(47, 10, 3, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-10 15:02:00'),
(48, 11, 1, 'estado', NULL, 'Abierto', '2026-09-22 09:17:00'),
(49, 11, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-22 11:33:00'),
(50, 11, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-22 12:09:00'),
(51, 11, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-22 12:40:00'),
(52, 12, 1, 'estado', NULL, 'Abierto', '2026-10-09 08:23:00'),
(53, 12, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-09 08:55:00'),
(54, 12, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-09 09:32:00'),
(55, 12, 4, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-09 10:32:00'),
(56, 13, 1, 'estado', NULL, 'Abierto', '2026-10-04 12:00:00'),
(57, 13, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-04 12:27:00'),
(58, 13, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-04 13:07:00'),
(59, 13, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-10-04 13:52:00'),
(60, 14, 1, 'estado', NULL, 'Abierto', '2026-10-09 11:25:00'),
(61, 14, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-09 14:18:00'),
(62, 14, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-09 14:51:00'),
(63, 14, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-10-09 16:01:00'),
(64, 15, 1, 'estado', NULL, 'Abierto', '2026-09-03 11:40:00'),
(65, 15, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-03 14:06:00'),
(66, 15, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-03 14:09:00'),
(67, 15, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-03 15:00:00'),
(68, 15, 1, 'estado', 'Resuelto', 'Cerrado', '2026-09-04 08:58:00'),
(69, 16, 2, 'estado', NULL, 'Abierto', '2026-10-11 13:00:00'),
(70, 17, 1, 'estado', NULL, 'Abierto', '2026-10-01 08:24:00'),
(71, 17, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-01 10:18:00'),
(72, 17, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-01 10:23:00'),
(73, 17, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-10-01 14:23:00'),
(74, 18, 1, 'estado', NULL, 'Abierto', '2026-10-09 14:51:00'),
(75, 19, 5, 'estado', NULL, 'Abierto', '2026-10-07 10:14:00'),
(76, 19, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-07 11:11:00'),
(77, 19, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-07 11:48:00'),
(78, 20, 1, 'estado', NULL, 'Abierto', '2026-10-09 10:25:00'),
(79, 21, 5, 'estado', NULL, 'Abierto', '2026-09-12 10:32:00'),
(80, 21, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-09-12 13:07:00'),
(81, 21, 3, 'estado', 'Abierto', 'En_Progreso', '2026-09-12 13:26:00'),
(82, 21, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-09-12 17:06:00'),
(83, 21, 5, 'estado', 'Resuelto', 'Cerrado', '2026-09-14 08:07:00'),
(84, 22, 2, 'estado', NULL, 'Abierto', '2026-09-17 12:03:00'),
(85, 22, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-17 12:49:00'),
(86, 22, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-17 13:08:00'),
(87, 22, 4, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-09-17 16:01:00'),
(88, 22, 4, 'estado', 'Pendiente_Usuario', 'En_Progreso', '2026-09-18 09:22:00'),
(89, 22, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-18 11:35:00'),
(90, 23, 5, 'estado', NULL, 'Abierto', '2026-09-17 09:32:00'),
(91, 23, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-17 11:29:00'),
(92, 23, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-17 12:07:00'),
(93, 23, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-17 16:55:00'),
(94, 23, 5, 'estado', 'Resuelto', 'Cerrado', '2026-09-18 08:28:00'),
(95, 24, 1, 'estado', NULL, 'Abierto', '2026-10-09 08:03:00'),
(96, 24, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-09 08:27:00'),
(97, 24, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-09 09:07:00'),
(98, 24, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-10-09 09:34:00'),
(99, 25, 1, 'estado', NULL, 'Abierto', '2026-09-08 14:26:00'),
(100, 25, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-08 15:02:00'),
(101, 25, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-08 15:29:00'),
(102, 25, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-08 17:46:00'),
(103, 25, 1, 'estado', 'Resuelto', 'Cerrado', '2026-09-09 08:40:00'),
(104, 26, 1, 'estado', NULL, 'Abierto', '2026-10-11 11:16:00'),
(105, 26, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-11 12:50:00'),
(106, 26, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-11 13:24:00'),
(107, 26, 4, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-11 14:44:00'),
(108, 27, 1, 'estado', NULL, 'Abierto', '2026-10-09 10:15:00'),
(109, 27, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-10-09 12:40:00'),
(110, 27, 4, 'estado', 'Abierto', 'En_Progreso', '2026-10-09 13:20:00'),
(111, 28, 1, 'estado', NULL, 'Abierto', '2026-10-09 11:04:00'),
(112, 29, 2, 'estado', NULL, 'Abierto', '2026-09-11 11:56:00'),
(113, 29, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-11 12:54:00'),
(114, 29, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-11 13:33:00'),
(115, 29, 4, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-09-11 16:58:00'),
(116, 29, 4, 'estado', 'Pendiente_Usuario', 'En_Progreso', '2026-09-12 08:25:00'),
(117, 29, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-12 08:51:00'),
(118, 30, 1, 'estado', NULL, 'Abierto', '2026-10-10 08:38:00'),
(119, 30, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-10 11:33:00'),
(120, 30, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-10 12:00:00'),
(121, 31, 1, 'estado', NULL, 'Abierto', '2026-10-01 08:28:00'),
(122, 31, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-01 08:46:00'),
(123, 31, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-01 08:53:00'),
(124, 31, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-10-01 11:15:00'),
(125, 31, 1, 'estado', 'Resuelto', 'Cerrado', '2026-10-03 08:26:00'),
(126, 32, 1, 'estado', NULL, 'Abierto', '2026-10-10 14:52:00'),
(127, 32, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-10 17:30:00'),
(128, 32, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-10 17:45:00'),
(129, 33, 2, 'estado', NULL, 'Abierto', '2026-10-08 09:14:00'),
(130, 33, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-08 11:51:00'),
(131, 33, 5, 'prioridad', 'Media', 'Alta', '2026-10-08 12:14:00'),
(132, 33, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-08 12:27:00'),
(133, 33, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-10-08 15:32:00'),
(134, 34, 1, 'estado', NULL, 'Abierto', '2026-09-29 10:34:00'),
(135, 34, 4, 'tecnico_asignado', NULL, 'Diego Fernandez Ibanez', '2026-09-29 12:04:00'),
(136, 34, 4, 'estado', 'Abierto', 'En_Progreso', '2026-09-29 12:22:00'),
(137, 34, 4, 'estado', 'En_Progreso', 'Resuelto', '2026-09-29 16:07:00'),
(138, 35, 2, 'estado', NULL, 'Abierto', '2026-09-09 12:49:00'),
(139, 35, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-09-09 14:47:00'),
(140, 35, 3, 'estado', 'Abierto', 'En_Progreso', '2026-09-09 15:02:00'),
(141, 35, 5, 'prioridad', 'Media', 'Alta', '2026-09-09 15:49:00'),
(142, 35, 3, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-09-09 17:00:00'),
(143, 35, 3, 'estado', 'Pendiente_Usuario', 'En_Progreso', '2026-09-10 11:38:00'),
(144, 35, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-09-10 12:09:00'),
(145, 36, 1, 'estado', NULL, 'Abierto', '2026-10-01 10:10:00'),
(146, 36, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-01 12:53:00'),
(147, 36, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-01 13:20:00'),
(148, 36, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-10-01 15:35:00'),
(149, 36, 1, 'estado', 'Resuelto', 'Cerrado', '2026-10-02 15:55:00'),
(150, 37, 2, 'estado', NULL, 'Abierto', '2026-10-10 14:36:00'),
(151, 38, 2, 'estado', NULL, 'Abierto', '2026-10-09 08:05:00'),
(152, 38, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-09 10:40:00'),
(153, 38, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-09 10:54:00'),
(154, 38, 3, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-09 13:05:00'),
(155, 39, 1, 'estado', NULL, 'Abierto', '2026-10-09 12:37:00'),
(156, 40, 2, 'estado', NULL, 'Abierto', '2026-10-11 11:40:00'),
(157, 41, 2, 'estado', NULL, 'Abierto', '2026-09-18 08:40:00'),
(158, 41, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-09-18 08:54:00'),
(159, 41, 3, 'estado', 'Abierto', 'En_Progreso', '2026-09-18 09:02:00'),
(160, 42, 2, 'estado', NULL, 'Abierto', '2026-10-11 16:17:00'),
(161, 43, 2, 'estado', NULL, 'Abierto', '2026-09-16 11:59:00'),
(162, 43, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-09-16 13:20:00'),
(163, 43, 3, 'estado', 'Abierto', 'En_Progreso', '2026-09-16 13:26:00'),
(164, 43, 3, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-09-16 14:27:00'),
(165, 43, 3, 'estado', 'Pendiente_Usuario', 'En_Progreso', '2026-09-17 09:26:00'),
(166, 43, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-09-17 10:06:00'),
(167, 43, 2, 'estado', 'Resuelto', 'Cerrado', '2026-09-17 14:13:00'),
(168, 44, 1, 'estado', NULL, 'Abierto', '2026-10-01 09:28:00'),
(169, 44, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-01 10:07:00'),
(170, 44, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-01 10:38:00'),
(171, 44, 3, 'estado', 'En_Progreso', 'Resuelto', '2026-10-01 15:00:00'),
(172, 44, 1, 'estado', 'Resuelto', 'Cerrado', '2026-10-03 08:11:00'),
(173, 45, 1, 'estado', NULL, 'Abierto', '2026-10-11 09:16:00'),
(174, 45, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-11 10:14:00'),
(175, 45, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-11 10:49:00'),
(176, 46, 2, 'estado', NULL, 'Abierto', '2026-10-09 12:35:00'),
(177, 46, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-09 13:43:00'),
(178, 46, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-09 14:20:00'),
(179, 46, 3, 'estado', 'En_Progreso', 'Pendiente_Usuario', '2026-10-09 17:24:00'),
(180, 47, 1, 'estado', NULL, 'Abierto', '2026-09-21 13:19:00'),
(181, 47, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-09-21 15:17:00'),
(182, 47, 3, 'estado', 'Abierto', 'En_Progreso', '2026-09-21 15:45:00'),
(183, 48, 1, 'estado', NULL, 'Abierto', '2026-10-11 15:25:00'),
(184, 48, 3, 'tecnico_asignado', NULL, 'Laura Gomez Martin', '2026-10-11 15:44:00'),
(185, 48, 3, 'estado', 'Abierto', 'En_Progreso', '2026-10-11 15:59:00');

INSERT INTO Comentarios_Ticket (id_comentario, id_ticket, id_usuario, comentario, es_interno, fecha_comentario) VALUES
(1, 1, 4, 'He revisado los permisos del usuario en el directorio, en curso.', FALSE, '2026-09-08 12:04:00'),
(2, 1, 4, 'Nota interna: causa conocida del catalogo interno, revisar antes con Laura Gomez Martin', TRUE, '2026-09-08 12:09:00'),
(3, 1, 4, 'Solucion aplicada: Se han concedido los permisos solicitados y el acceso queda activo. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-08 14:32:00'),
(4, 1, 2, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-09-09 08:23:00'),
(5, 2, 4, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-09-11 09:16:00'),
(6, 2, 4, 'Para avanzar necesito que me confirmes desde que fecha exacta falta el correo.', FALSE, '2026-09-11 13:25:00'),
(7, 3, 3, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-10-01 11:35:00'),
(8, 3, 3, 'Para avanzar necesito que me confirmes si el fallo ocurre tambien conectado a otro enchufe.', FALSE, '2026-10-01 17:41:00'),
(9, 3, 2, 'Confirmado: con otro enchufe sigue igual, te paso la serie del equipo por si sirve', FALSE, '2026-10-02 15:27:00'),
(10, 3, 3, 'Solucion aplicada: Se ha sustituido el componente defectuoso y el equipo vuelve a operar con normalidad. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-02 17:39:00'),
(11, 3, 2, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-10-03 14:30:00'),
(12, 4, 3, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-09-15 13:09:00'),
(13, 4, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-09-15 13:14:00'),
(14, 4, 3, 'Solucion aplicada: Se ha reparado la instalacion y actualizado la aplicacion; el fallo ya no se reproduce. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-15 17:59:00'),
(15, 5, 3, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-10-07 14:15:00'),
(16, 5, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-10-07 14:20:00'),
(17, 5, 3, 'Solucion aplicada: Se ha sustituido el componente defectuoso y el equipo vuelve a operar con normalidad. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-07 17:10:00'),
(18, 5, 1, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-10-08 15:38:00'),
(19, 6, 4, 'He revisado los permisos del usuario en el directorio, en curso.', FALSE, '2026-10-10 13:10:00'),
(20, 7, 4, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-10-10 11:11:00'),
(21, 7, 4, 'Para avanzar necesito que me confirmes la version exacta que aparece en el pie de la aplicacion.', FALSE, '2026-10-10 17:27:00'),
(22, 7, 1, 'Te confirmo la version que me pides: es la 8.4.2, la ultima que nos ofrece el fabricante', FALSE, '2026-10-11 08:26:00'),
(23, 7, 4, 'Solucion aplicada: Se ha reparado la instalacion y actualizado la aplicacion; el fallo ya no se reproduce. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-11 12:41:00'),
(24, 9, 4, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-10-02 12:01:00'),
(25, 9, 4, 'Para avanzar necesito que me confirmes la version exacta que aparece en el pie de la aplicacion.', FALSE, '2026-10-02 13:18:00'),
(26, 9, 2, 'Te confirmo la version que me pides: es la 8.4.2, la ultima que nos ofrece el fabricante', FALSE, '2026-10-03 13:23:00'),
(27, 9, 4, 'Solucion aplicada: Se ha reparado la instalacion y actualizado la aplicacion; el fallo ya no se reproduce. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-03 16:05:00'),
(28, 9, 2, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-10-04 14:27:00'),
(29, 10, 3, 'He revisado los permisos del usuario en el directorio, en curso.', FALSE, '2026-10-10 13:13:00'),
(30, 10, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-10-10 13:18:00'),
(31, 10, 3, 'Para avanzar necesito que me confirmes el usuario y el departamento exactos del solicitante.', FALSE, '2026-10-10 15:02:00'),
(32, 11, 4, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-09-22 12:09:00'),
(33, 11, 4, 'Solucion aplicada: Se ha reconstruido el perfil de correo y la sincronizacion funciona de nuevo. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-22 12:40:00'),
(34, 12, 4, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-10-09 09:32:00'),
(35, 12, 4, 'Para avanzar necesito que me confirmes la version exacta que aparece en el pie de la aplicacion.', FALSE, '2026-10-09 10:32:00'),
(36, 13, 3, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-10-04 13:07:00'),
(37, 13, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-10-04 13:12:00'),
(38, 13, 3, 'Solucion aplicada: Se ha reparado la instalacion y actualizado la aplicacion; el fallo ya no se reproduce. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-04 13:52:00'),
(39, 14, 4, 'He revisado los permisos del usuario en el directorio, en curso.', FALSE, '2026-10-09 14:51:00'),
(40, 14, 4, 'Solucion aplicada: Se han concedido los permisos solicitados y el acceso queda activo. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-09 16:01:00'),
(41, 15, 4, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-09-03 14:09:00'),
(42, 15, 4, 'Solucion aplicada: Se ha sustituido el componente defectuoso y el equipo vuelve a operar con normalidad. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-03 15:00:00'),
(43, 15, 1, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-09-04 08:58:00'),
(44, 17, 4, 'He revisado la conexion y los logs de la pasarela, en curso.', FALSE, '2026-10-01 10:23:00'),
(45, 17, 4, 'Solucion aplicada: Se ha renovado la configuracion de red y la conexion vuelve a establecerse sin errores. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-01 14:23:00'),
(46, 19, 4, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-10-07 11:48:00'),
(47, 21, 3, 'He revisado la conexion y los logs de la pasarela, en curso.', FALSE, '2026-09-12 13:26:00'),
(48, 21, 3, 'Solucion aplicada: Se ha renovado la configuracion de red y la conexion vuelve a establecerse sin errores. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-12 17:06:00'),
(49, 21, 5, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-09-14 08:07:00'),
(50, 22, 4, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-09-17 13:08:00'),
(51, 22, 4, 'Para avanzar necesito que me confirmes desde que fecha exacta falta el correo.', FALSE, '2026-09-17 16:01:00'),
(52, 22, 2, 'El fallo empezo el lunes 5 de octubre, desde primera hora de la manana', FALSE, '2026-09-18 09:22:00'),
(53, 22, 4, 'Solucion aplicada: Se ha reconstruido el perfil de correo y la sincronizacion funciona de nuevo. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-18 11:35:00'),
(54, 23, 4, 'He revisado los permisos del usuario en el directorio, en curso.', FALSE, '2026-09-17 12:07:00'),
(55, 23, 4, 'Solucion aplicada: Se han concedido los permisos solicitados y el acceso queda activo. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-17 16:55:00'),
(56, 23, 5, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-09-18 08:28:00'),
(57, 24, 4, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-10-09 09:07:00'),
(58, 24, 4, 'Solucion aplicada: Se ha sustituido el componente defectuoso y el equipo vuelve a operar con normalidad. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-09 09:34:00'),
(59, 25, 4, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-09-08 15:29:00'),
(60, 25, 4, 'Solucion aplicada: Se ha reconstruido el perfil de correo y la sincronizacion funciona de nuevo. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-08 17:46:00'),
(61, 25, 1, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-09-09 08:40:00'),
(62, 26, 4, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-10-11 13:24:00'),
(63, 26, 4, 'Nota interna: causa conocida del catalogo interno, revisar antes con Laura Gomez Martin', TRUE, '2026-10-11 13:29:00'),
(64, 26, 4, 'Para avanzar necesito que me confirmes desde que fecha exacta falta el correo.', FALSE, '2026-10-11 14:44:00'),
(65, 27, 4, 'He revisado los permisos del usuario en el directorio, en curso.', FALSE, '2026-10-09 13:20:00'),
(66, 27, 4, 'Nota interna: causa conocida del catalogo interno, revisar antes con Laura Gomez Martin', TRUE, '2026-10-09 13:25:00'),
(67, 29, 4, 'He revisado la conexion y los logs de la pasarela, en curso.', FALSE, '2026-09-11 13:33:00'),
(68, 29, 4, 'Para avanzar necesito que me confirmes si el fallo ocurre tambien con cable y no solo por Wi-Fi.', FALSE, '2026-09-11 16:58:00'),
(69, 29, 2, 'Acabo de probar con cable y ocurre exactamente igual desde esta manana', FALSE, '2026-09-12 08:25:00'),
(70, 29, 4, 'Solucion aplicada: Se ha renovado la configuracion de red y la conexion vuelve a establecerse sin errores. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-12 08:51:00'),
(71, 30, 3, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-10-10 12:00:00'),
(72, 30, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-10-10 12:05:00'),
(73, 31, 3, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-10-01 08:53:00'),
(74, 31, 3, 'Solucion aplicada: Se ha reconstruido el perfil de correo y la sincronizacion funciona de nuevo. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-01 11:15:00'),
(75, 31, 1, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-10-03 08:26:00'),
(76, 32, 3, 'He revisado la conexion y los logs de la pasarela, en curso.', FALSE, '2026-10-10 17:45:00'),
(77, 33, 3, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-10-08 12:27:00'),
(78, 33, 3, 'Solucion aplicada: Se ha sustituido el componente defectuoso y el equipo vuelve a operar con normalidad. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-08 15:32:00'),
(79, 34, 4, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-09-29 12:22:00'),
(80, 34, 4, 'Solucion aplicada: Se ha sustituido el componente defectuoso y el equipo vuelve a operar con normalidad. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-29 16:07:00'),
(81, 35, 3, 'He revisado la conexion y los logs de la pasarela, en curso.', FALSE, '2026-09-09 15:02:00'),
(82, 35, 3, 'Para avanzar necesito que me confirmes si el fallo ocurre tambien con cable y no solo por Wi-Fi.', FALSE, '2026-09-09 17:00:00'),
(83, 35, 2, 'Acabo de probar con cable y ocurre exactamente igual desde esta manana', FALSE, '2026-09-10 11:38:00'),
(84, 35, 3, 'Solucion aplicada: Se ha renovado la configuracion de red y la conexion vuelve a establecerse sin errores. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-10 12:09:00'),
(85, 36, 3, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-10-01 13:20:00'),
(86, 36, 3, 'Solucion aplicada: Se ha reparado la instalacion y actualizado la aplicacion; el fallo ya no se reproduce. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-01 15:35:00'),
(87, 36, 1, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-10-02 15:55:00'),
(88, 38, 3, 'He revisado la conexion y los logs de la pasarela, en curso.', FALSE, '2026-10-09 10:54:00'),
(89, 38, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-10-09 10:59:00'),
(90, 38, 3, 'Para avanzar necesito que me confirmes si el fallo ocurre tambien con cable y no solo por Wi-Fi.', FALSE, '2026-10-09 13:05:00'),
(91, 41, 3, 'He revisado el equipo en su puesto de trabajo, en curso.', FALSE, '2026-09-18 09:02:00'),
(92, 43, 3, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-09-16 13:26:00'),
(93, 43, 3, 'Para avanzar necesito que me confirmes desde que fecha exacta falta el correo.', FALSE, '2026-09-16 14:27:00'),
(94, 43, 2, 'El fallo empezo el lunes 5 de octubre, desde primera hora de la manana', FALSE, '2026-09-17 09:26:00'),
(95, 43, 3, 'Solucion aplicada: Se ha reconstruido el perfil de correo y la sincronizacion funciona de nuevo. Quedo a la espera de tu confirmacion.', FALSE, '2026-09-17 10:06:00'),
(96, 43, 2, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-09-17 14:13:00'),
(97, 44, 3, 'He revisado los permisos del usuario en el directorio, en curso.', FALSE, '2026-10-01 10:38:00'),
(98, 44, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-10-01 10:43:00'),
(99, 44, 3, 'Solucion aplicada: Se han concedido los permisos solicitados y el acceso queda activo. Quedo a la espera de tu confirmacion.', FALSE, '2026-10-01 15:00:00'),
(100, 44, 1, 'Confirmado, todo funciona correctamente. Muchas gracias.', FALSE, '2026-10-03 08:11:00'),
(101, 45, 3, 'He revisado la conexion y los logs de la pasarela, en curso.', FALSE, '2026-10-11 10:49:00'),
(102, 46, 3, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-10-09 14:20:00'),
(103, 46, 3, 'Nota interna: causa conocida del catalogo interno, revisar antes con Diego Fernandez Ibanez', TRUE, '2026-10-09 14:25:00'),
(104, 46, 3, 'Para avanzar necesito que me confirmes desde que fecha exacta falta el correo.', FALSE, '2026-10-09 17:24:00'),
(105, 47, 3, 'He revisado la cola del cliente de correo, en curso.', FALSE, '2026-09-21 15:45:00'),
(106, 48, 3, 'He revisado la aplicacion en el equipo del usuario, en curso.', FALSE, '2026-10-11 15:59:00');

INSERT INTO Valoraciones (id_valoracion, id_ticket, puntuacion, comentario, fecha_valoracion) VALUES
(1, 1, 4, 'Rapido y sin incidencias, gracias.', '2026-09-09 08:28:00'),
(2, 3, 4, 'Rapido y sin incidencias, gracias.', '2026-10-03 14:35:00'),
(3, 5, 5, 'Resuelto a la primera, muy buen trabajo.', '2026-10-08 15:43:00'),
(4, 9, 5, 'Resuelto a la primera, muy buen trabajo.', '2026-10-04 14:32:00'),
(5, 15, 4, 'Rapido y sin incidencias, gracias.', '2026-09-04 09:03:00'),
(6, 21, 4, 'Rapido y sin incidencias, gracias.', '2026-09-14 08:12:00'),
(7, 23, 4, 'Rapido y sin incidencias, gracias.', '2026-09-18 08:33:00'),
(8, 25, 4, 'Rapido y sin incidencias, gracias.', '2026-09-09 08:45:00'),
(9, 31, 5, 'Resuelto a la primera, muy buen trabajo.', '2026-10-03 08:31:00'),
(10, 36, 3, 'Resuelto, aunque algo lento para mi gusto.', '2026-10-02 16:00:00'),
(11, 43, 5, 'Resuelto a la primera, muy buen trabajo.', '2026-09-17 14:18:00'),
(12, 44, 4, 'Rapido y sin incidencias, gracias.', '2026-10-03 08:16:00');

INSERT INTO Adjuntos_Ticket (id_adjunto, id_ticket, nombre_archivo, ruta, tamano_kb, fecha_subida) VALUES
(1, 7, 'log-aplicacion.txt', '/adjuntos/ticket_7/log-aplicacion.txt', 24, '2026-10-10 09:19:00'),
(2, 14, 'permisos-directorio.png', '/adjuntos/ticket_14/permisos-directorio.png', 210, '2026-10-09 11:37:00'),
(3, 21, 'diagnostico-red.txt', '/adjuntos/ticket_21/diagnostico-red.txt', 18, '2026-09-12 10:44:00'),
(4, 28, 'log-aplicacion.txt', '/adjuntos/ticket_28/log-aplicacion.txt', 24, '2026-10-09 11:16:00'),
(5, 35, 'diagnostico-red.txt', '/adjuntos/ticket_35/diagnostico-red.txt', 18, '2026-09-09 13:01:00'),
(6, 42, 'diagnostico-red.txt', '/adjuntos/ticket_42/diagnostico-red.txt', 18, '2026-10-11 16:29:00');
-- >>> DATOS_GENERADOS_FIN <<<
