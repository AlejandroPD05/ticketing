# Helpdesk RoyalTown — sistema de tickets

Sistema de tickets completo para un helpdesk interno: **esquema MySQL trazable,
capa de servicios en Java con JDBC y transacciones, CLI de demostración por
roles y 21 pruebas JUnit** que corren sobre el mismo script SQL.

| Capa     | Tecnología                                     |
| -------- | ---------------------------------------------- |
| Datos    | MySQL 8 (H2 en modo MySQL para las pruebas)    |
| Backend  | Java 21, JDBC a mano, Maven                    |
| Seguridad| bcrypt (`at.favre.lib:bcrypt`)                 |
| Pruebas  | JUnit 5                                        |
| Demo web | HTML/CSS/JS con tema claro-oscuro y ES/EN      |

## Qué lo diferencia de un CRUD cualquiera

- **Ciclo de vida validado en base de datos.** `Transiciones_Estado` es una
  matriz de las 10 transiciones legales; la capa de servicios la carga en
  memoria y rechaza saltos no permitidos (por ejemplo `Abierto → Cerrado`)
  listando los destinos válidos en la excepción.
- **Auditoría campo a campo.** Todo cambio de un ticket escribe una fila en
  `Historial_Ticket` (campo, valor anterior, valor nuevo, autor y fecha) dentro
  de la misma transacción que la modificación: o se aplican las dos o ninguna.
- **SLA por prioridad.** `Prioridades.sla_horas` define el plazo; cada ticket
  guarda `fecha_limite = fecha_creacion + SLA` y la vista `vw_sla_incumplido`
  saca los incumplidos.
- **Permisos explícitos.** Solo técnicos y administradores asignan, transicionan
  o cambian prioridad; el empleado creador únicamente puede cerrar confirmando
  una resolución, comentar en público y valorar su ticket (una vez, y solo
  estando cerrado).
- **Vistas de KPI.** `vw_tickets_abiertos`, `vw_sla_incumplido`,
  `vw_resolucion_por_tecnico` y `vw_tickets_por_estado` responden los informes
  sin lógica en Java.

## Modelo de datos

11 tablas, 4 vistas y 7 índices:

```
Roles ─┬─< Usuarios ──┬─< Tickets >──┬─< Comentarios_Ticket
       │              │              ├─< Historial_Ticket
Prioridades ──────────┤              ├─< Adjuntos_Ticket
Estados ──┬───────────┤              └─< Valoraciones (1 a 1, UNIQUE)
          └─< Transiciones_Estado
Categorias ───────────┘
```

Estados: `Abierto(1) → En_Progreso(2) ⇄ Pendiente_Usuario(3) → Resuelto(4) →
Cerrado(5)`, con reaperturas `Resuelto/Cerrado → En_Progreso`.

## Arquitectura

```
src/main/java/com/royaltown/helpdesk/
├── cli/         Main y Consola: menús por rol, sin SQL ni reglas de negocio
├── service/     TicketService, AuthService, ReporteService, CatalogoService
├── dao/         Unit of work: los escritos reciben la Connection abierta
├── model/       Ticket, Usuario, Estado, Prioridad, Catálogo (inmutables)
├── util/        ConexionDB, PasswordHash (bcrypt), Transaccion
└── exception/   HelpdeskException: violaciones de regla de negocio
```

Los DAO abren PreparedStatement siempre (cero concatenación de SQL) y los
servicios envuelven cada mutación en `Transaccion.ejecutar(...)`.

## Puesta en marcha

Requisitos: JDK 21, Maven 3.9+, Docker (o un MySQL 8 propio).

```bash
# 1. Levantar MySQL 8
docker run -d --name ticketing-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=helpdesk \
  -e MYSQL_USER=helpdesk -e MYSQL_PASSWORD=helpdesk mysql:8

# 2. Cargar esquema + datos de ejemplo
docker exec -i ticketing-mysql mysql --default-character-set=utf8mb4 \
  -uhelpdesk -phelpdesk helpdesk < schema.sql

# 3. Lanzar la CLI
mvn -q compile exec:java
```

### Usuarios de prueba

Todos con contraseña `royaltown2026` (hashes bcrypt reales en la semilla):

| Email                                | Rol       |
| ------------------------------------ | --------- |
| marta.sanchez@royaltown.local        | empleado  |
| javier.ruiz@royaltown.local          | empleado  |
| laura.gomez@royaltown.local          | técnico   |
| diego.fernandez@royaltown.local      | técnico   |
| alejandro.ortiz@royaltown.local      | admin     |

La semilla genera 48 tickets con ciclo de vida coherente (creación →
asignación → espera → resolución → cierre), 185 cambios de historial, 106
comentarios, 12 valoraciones y 6 adjuntos: 24 abiertos y 24 cerrados, 15 de
ellos fuera de SLA.

### Configuración

`ConexionDB` resuelve la conexión en este orden: variables de entorno →
`src/main/resources/db.properties` → valores por defecto
(`jdbc:mysql://localhost:3306/helpdesk`, `helpdesk`/`helpdesk`).

`db.properties` está en `.gitignore` (no sube credenciales): copia
`src/main/resources/db.properties.example` a `db.properties` y ajústalo.

```bash
HELPDESK_DB_URL=jdbc:mysql://localhost:3306/helpdesk
HELPDESK_DB_USER=helpdesk
HELPDESK_DB_PASSWORD=helpdesk
```

## Pruebas

```bash
mvn test
```

21 pruebas (permisos, máquina de estados, SLA, auditoría, transacciones,
valoraciones, autenticación e informes) sobre **H2 en modo MySQL** que cargan
el `schema.sql` real vía `RunScript`: si el esquema se rompe, se rompen las
pruebas. Por convención, las clases de prueba terminan en `*Pruebas`.

## Regenerar los datos de ejemplo

`tools/seed_generator.py` es determinista (semilla fija) y reescribe el bloque
de datos de `schema.sql` entre sus marcadores:

```bash
pip install bcrypt
python tools/seed_generator.py
```

## Demo web

`index.html` (+ `css/` y `js/`) es la página del proyecto con el esquema
completo, el diagrama entidad-relación (Mermaid), el ciclo de vida, la
arquitectura Java y el script SQL completo tal cual se sirve desde
`schema.sql`. Se publica con GitHub Pages y respeta los contratos del
portfolio:

- tema claro/oscuro y ES/EN persistentes;
- efectos (reveal, tilt, contadores, scramble) desactivados con
  `prefers-reduced-motion`, forzables con `?motion=on` y apagables con
  `?motion=off`;
- sin JavaScript el contenido queda visible.

## Nota de consola (Windows)

Si la CLI muestra `?` en lugar de `¿` o tildes, es el codepage de la consola,
no la aplicación:

```powershell
$env:MAVEN_OPTS = "-Dstdout.encoding=UTF-8"
```
