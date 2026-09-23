CREATE TABLE Usuarios (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('empleado', 'tecnico', 'administrador')),
    departamento VARCHAR(80),
    fecha_alta TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    estado VARCHAR(20) NOT NULL DEFAULT 'Abierto'
        CHECK (estado IN ('Abierto', 'En_Progreso', 'Pendiente_Usuario', 'Resuelto', 'Cerrado')),
    prioridad VARCHAR(10) NOT NULL
        CHECK (prioridad IN ('Baja', 'Media', 'Alta', 'Critica')),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMP NULL,
    id_categoria INT NOT NULL,
    usuario_creador INT NOT NULL,
    tecnico_asignado INT NULL,
    CONSTRAINT fk_tickets_categoria FOREIGN KEY (id_categoria)
        REFERENCES Categorias (id_categoria)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_usuario_creador FOREIGN KEY (usuario_creador)
        REFERENCES Usuarios (id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_tecnico_asignado FOREIGN KEY (tecnico_asignado)
        REFERENCES Usuarios (id_usuario)
        ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE Comentarios_Ticket (
    id_comentario INT AUTO_INCREMENT PRIMARY KEY,
    id_ticket INT NOT NULL,
    id_usuario INT NOT NULL,
    comentario TEXT NOT NULL,
    fecha_comentario TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comentarios_ticket FOREIGN KEY (id_ticket)
        REFERENCES Tickets (id_ticket)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_comentarios_usuario FOREIGN KEY (id_usuario)
        REFERENCES Usuarios (id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

INSERT INTO Usuarios (nombre_completo, email, password_hash, rol, departamento) VALUES
('Marta Sánchez López', 'marta.sanchez@royaltown.local', '$2y$10$Qk1z8f5mR3xW7pLc9vNq1eS0bH4dY6tJ8uK2aF5gC7hM3nP9rT1Xa', 'empleado', 'Contabilidad'),
('Javier Torres Ruiz', 'javier.torres@royaltown.local', '$2y$10$Nf3m9c2xL8pQw1rT6yU4vE0jH7dS5aK3gB9nM2cF1hR8tW6oP4Ybc', 'empleado', 'Recursos Humanos'),
('Laura Gómez Martín', 'laura.gomez@royaltown.local', '$2y$10$Zp7q2w9eR4tY6uI1oA3sD5fG8hJ0kL2mN4bV6cX8zQ1wE3rT5yU7v', 'tecnico', 'Soporte IT'),
('Diego Fernández Ibáñez', 'diego.fernandez@royaltown.local', '$2y$10$Ht5g8j1kL3mN6bV9cX2zQ4wE7rT0yU3iO5pA8sD1fG4hJ7kL0mN2q', 'tecnico', 'Soporte IT'),
('Alejandro Ortiz Vega', 'alejandro.ortiz@royaltown.local', '$2y$10$Bx4c7v0zQ2wE5rT8yU1iO3pA6sD9fG1hJ4kL7mN0bV3cX6zQ9wE2rT', 'administrador', 'Sistemas');

INSERT INTO Categorias (nombre, descripcion) VALUES
('Hardware', 'Incidencias relacionadas con equipos físicos, periféricos y dispositivos de usuario.'),
('Software', 'Instalación, actualización o fallos de aplicaciones y sistemas operativos.'),
('Redes', 'Conectividad, VPN, Wi-Fi y acceso a recursos de red interna.'),
('Accesos', 'Altas, bajas y permisos de usuario en sistemas y aplicaciones corporativas.'),
('Correo y Comunicaciones', 'Incidencias de correo electrónico, calendario y herramientas de mensajería.');

INSERT INTO Tickets (titulo, descripcion, estado, prioridad, fecha_creacion, fecha_cierre, id_categoria, usuario_creador, tecnico_asignado) VALUES
('El ordenador no enciende tras el corte de luz', 'Tras el corte de luz de esta mañana, el equipo de sobremesa no responde al pulsar el botón de encendido. El piloto de red eléctrica está apagado.', 'Resuelto', 'Alta', '2026-09-02 08:15:00', '2026-09-02 11:40:00', 1, 1, 3),
('No puedo conectarme a la VPN desde casa', 'Al intentar iniciar el cliente VPN desde el portátil corporativo, la conexión falla con un error de autenticación tras varios intentos.', 'En_Progreso', 'Alta', '2026-09-08 09:02:00', NULL, 3, 2, 4),
('Solicito acceso a la carpeta compartida de Nóminas', 'Necesito acceso de lectura a la carpeta compartida de Nóminas para poder consultar los justificantes del mes en curso.', 'Pendiente_Usuario', 'Media', '2026-09-09 10:30:00', NULL, 4, 1, 3),
('Outlook no sincroniza el correo desde ayer', 'El cliente de Outlook se queda cargando indefinidamente al intentar sincronizar la bandeja de entrada desde ayer por la tarde.', 'Abierto', 'Media', '2026-09-10 16:45:00', NULL, 5, 2, NULL),
('La impresora de planta 2 no responde', 'La impresora compartida de la planta 2 aparece como desconectada para todos los usuarios de esa zona desde esta mañana.', 'Cerrado', 'Baja', '2026-09-05 09:20:00', '2026-09-05 12:10:00', 1, 5, 4);

INSERT INTO Comentarios_Ticket (id_ticket, id_usuario, comentario, fecha_comentario) VALUES
(1, 3, 'Revisado in situ. La fuente de alimentación se había dañado por el corte de luz. Sustituida y equipo operativo de nuevo.', '2026-09-02 11:15:00'),
(1, 1, 'Confirmado, el equipo ya enciende con normalidad. Gracias por la rapidez.', '2026-09-02 11:38:00'),
(2, 4, 'Revisado el certificado VPN del usuario, parece caducado. Generando uno nuevo y reenviando instrucciones de instalación.', '2026-09-08 12:20:00'),
(3, 3, 'Pendiente de validación por parte del responsable de Nóminas antes de conceder el acceso solicitado.', '2026-09-09 13:05:00'),
(5, 4, 'Cambiado el cable de red de la impresora y reiniciado el servicio de cola de impresión. Incidencia cerrada.', '2026-09-05 12:05:00');