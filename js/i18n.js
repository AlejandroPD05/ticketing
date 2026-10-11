/* ==========================================================================
   i18n.js — ES/EN con el mismo patrón que el portfolio.

   Marca en el HTML:
     data-i18n="clave"              -> textContent
     data-i18n-title="clave"        -> atributo title
     data-i18n-label="clave"        -> aria-label
     data-i18n-placeholder="clave"  -> placeholder

   El idioma se guarda en localStorage; al cambiarlo se lanza el evento
   "idioma:cambiado" para que el scramble vuelva a animar sus titulares.
   ========================================================================== */

(function () {
  "use strict";

  var CLAVE = "ayuda-idioma";

  var TEXTOS = {
    es: {
      "nav.schema": "Modelo de datos",
      "nav.er": "Diagrama ER",
      "nav.lifecycle": "Ciclo de vida",
      "nav.architecture": "Arquitectura",
      "nav.demo": "Datos",
      "nav.sql": "Script SQL",

      "toggle.lang": "Cambiar a inglés",
      "repo.aria": "Repositorio en GitHub",

      "hero.eyebrow": "Proyecto de portfolio · Helpdesk",
      "hero.title": "Tickets con ciclo de vida trazable",
      "hero.lead": "Esquema MySQL completo con 11 tablas, 4 vistas y una matriz de transiciones validada en base de datos; capa de servicios en Java con JDBC y transacciones; CLI de demostración por roles y 21 pruebas JUnit que corren sobre el mismo script. Cada mutación de un ticket queda auditada campo a campo en Historial_Ticket.",
      "hero.cta.sql": "Ver script SQL",
      "hero.cta.github": "Código en GitHub",
      "hero.stat.tables": "tablas",
      "hero.stat.views": "vistas",
      "hero.stat.tickets": "tickets de ejemplo",
      "hero.stat.tests": "tests en verde",

      "schema.title": "Modelo de datos",
      "schema.lead": "Nada de CHECK embebidos: roles, prioridades y estados son tablas lookup con clave foránea, y Transiciones_Estado codifica en base de datos qué cambios de estado son legales. Cada ticket guarda su fecha límite (creación + SLA de su prioridad) y los índices cubren las búsquedas calientes.",
      "schema.rows.lookup": "lookup con FK",
      "schema.rows.seed": "filas en la semilla",
      "schema.rows.vista": "KPI calculado en BD",

      "er.title": "Diagrama entidad-relación",
      "er.lead": "Claves primarias, foráneas y cardinalidades de las once tablas. Si Mermaid no puede cargarse sin conexión, la lista de relaciones de debajo sigue siendo la fuente de verdad.",
      "er.fallback": "El diagrama necesita red para cargar Mermaid; las relaciones completas están en la lista siguiente.",

      "lifecycle.title": "Ciclo de vida del ticket",
      "lifecycle.lead": "Diez transiciones permitidas, validadas por la capa de servicios contra el catálogo en memoria. Un salto no listado —por ejemplo Abierto → Cerrado— se rechaza y la excepción lista los destinos legales.",
      "lc.abierto.desc": "Recién creado, en cola",
      "lc.progreso.desc": "En manos de un técnico",
      "lc.pendiente.desc": "Esperando al usuario",
      "lc.resuelto.desc": "Solución aplicada, fija cierre",
      "lc.cerrado.desc": "Confirmado por el usuario",
      "lc.th.origen": "Origen",
      "lc.th.destino": "Destino",
      "lc.th.quien": "Quién lo ejecuta",
      "lc.th.motivo": "Motivo",
      "lc.who.soporte": "Técnico / admin",
      "lc.who.usuario": "Usuario creador",
      "lc.motivo.cargo": "El técnico se hace cargo",
      "lc.motivo.pide": "Se pide aclaración al reportero",
      "lc.motivo.cola": "Se devuelve a la cola",
      "lc.motivo.falta": "Falta información del usuario",
      "lc.motivo.resuelve": "Incidencia solucionada: empieza el reloj de resolución",
      "lc.motivo.responde": "El usuario responde",
      "lc.motivo.reinicia": "Se reinicia la indagación",
      "lc.motivo.confirma": "El usuario confirma la solución",
      "lc.motivo.reabre": "Reapertura: no era la solución",
      "lc.motivo.tardia": "Reapertura tardía por soporte",

      "arq.title": "Arquitectura Java",
      "arq.lead": "JDBC sin framework: la CLI no contiene SQL ni reglas de negocio. Los servicios abren la transacción, los DAO reciben la Connection (unit of work) y cualquier violación de una regla revierte la operación completa.",
      "arq.cli.name": "CLI",
      "arq.cli.desc": "Menús por rol —empleado, técnico y administrador— para crear, asignar, transicionar, comentar, valorar y consultar informes. Pinta datos y captura excepciones; no decide reglas.",
      "arq.service.name": "Servicios",
      "arq.service.desc": "TicketService concentra permisos, máquina de estados, SLA y auditoría. Cada mutación escribe ticket + historial en la misma transacción: o se aplican las dos o no se aplica ninguna.",
      "arq.dao.name": "DAO / JDBC",
      "arq.dao.desc": "Patrón unit-of-work: los escritos reciben la Connection abierta y las lecturas abren la suya. PreparedStatement en todas partes, cero concatenación de SQL.",
      "arq.db.name": "MySQL 8",
      "arq.db.desc": "Vistas para los KPIs —cola, SLA incumplido y resolución media por técnico—, índices sobre las búsquedas calientes y constraints a nivel de datos.",
      "arq.packages": "Paquetes",

      "datos.title": "Datos de ejemplo",
      "datos.lead": "48 tickets generados con ciclo de vida coherente —creación, asignación, incidencias en espera, resolución y cierre— por tools/seed_generator.py: 185 cambios de historial, 106 comentarios, valoraciones y hashes bcrypt reales.",
      "datos.credentials": "Demo: cualquier usuario de la semilla entra con la contraseña royaltown2026 (marta.sanchez@royaltown.local es empleada; laura.gomez@royaltown.local, técnica; alejandro.ortiz@royaltown.local, administrador).",
      "datos.th.id": "ID",
      "datos.th.titulo": "Título",
      "datos.th.cat": "Categoría",
      "datos.th.prio": "Prioridad",
      "datos.th.estado": "Estado",
      "datos.th.tecnico": "Técnico",
      "datos.th.fecha": "Creación",
      "datos.muestra": "Muestra de la tabla Tickets",

      "sql.title": "Script SQL completo",
      "sql.copy": "Copiar script",
      "sql.copied": "¡Copiado!",
      "sql.loading": "Cargando schema.sql…",
      "sql.fallback": "No se pudo cargar el script; ábrelo directamente en el repositorio.",
      "sql.note": "Se sirve desde schema.sql, el mismo fichero que Maven carga en las pruebas. Vale para MySQL 8 y para H2 en modo MySQL.",

      "footer.text": "Proyecto de portfolio · helpdesk RoyalTown",
      "footer.repo": "Ver repositorio"
    },

    en: {
      "nav.schema": "Data model",
      "nav.er": "ER diagram",
      "nav.lifecycle": "Lifecycle",
      "nav.architecture": "Architecture",
      "nav.demo": "Sample data",
      "nav.sql": "SQL script",

      "toggle.lang": "Cambiar a español",
      "repo.aria": "GitHub repository",

      "hero.eyebrow": "Portfolio project · Helpdesk",
      "hero.title": "Tickets with a traceable lifecycle",
      "hero.lead": "A full MySQL schema with 11 tables, 4 views and a database-enforced transition matrix; a Java service layer over JDBC with real transactions; a role-based demo CLI; and 21 JUnit tests running against the same script. Every ticket change is audited field by field in Historial_Ticket.",
      "hero.cta.sql": "View the SQL script",
      "hero.cta.github": "Source on GitHub",
      "hero.stat.tables": "tables",
      "hero.stat.views": "views",
      "hero.stat.tickets": "sample tickets",
      "hero.stat.tests": "passing tests",

      "schema.title": "Data model",
      "schema.lead": "No inline CHECKs: roles, priorities and statuses are lookup tables joined by foreign keys, and Transiciones_Estado encodes which status changes are legal. Every ticket stores its deadline (creation + its priority's SLA), and indexes cover the hot queries.",
      "schema.rows.lookup": "FK lookup",
      "schema.rows.seed": "rows in the seed",
      "schema.rows.vista": "KPI computed in the DB",

      "er.title": "Entity-relationship diagram",
      "er.lead": "Primary keys, foreign keys and cardinalities of the eleven tables. If Mermaid cannot load offline, the relationship list below remains the source of truth.",
      "er.fallback": "The diagram needs network access to load Mermaid; all relationships are listed below.",

      "lifecycle.title": "Ticket lifecycle",
      "lifecycle.lead": "Ten allowed transitions, validated by the service layer against the in-memory catalogue. An unlisted jump —say Abierto → Cerrado— is rejected and the exception lists the legal targets.",
      "lc.abierto.desc": "Just created, in the queue",
      "lc.progreso.desc": "Being handled by a technician",
      "lc.pendiente.desc": "Waiting on the reporter",
      "lc.resuelto.desc": "Fix applied, closure recorded",
      "lc.cerrado.desc": "Confirmed by the reporter",
      "lc.th.origen": "From",
      "lc.th.destino": "To",
      "lc.th.quien": "Performed by",
      "lc.th.motivo": "Reason",
      "lc.who.soporte": "Technician / admin",
      "lc.who.usuario": "Reporter",
      "lc.motivo.cargo": "A technician picks it up",
      "lc.motivo.pide": "More info requested from the reporter",
      "lc.motivo.cola": "Back to the queue",
      "lc.motivo.falta": "Missing information from the user",
      "lc.motivo.resuelve": "Issue fixed: the resolution clock starts",
      "lc.motivo.responde": "The reporter replies",
      "lc.motivo.reinicia": "Investigation restarts",
      "lc.motivo.confirma": "The reporter confirms the fix",
      "lc.motivo.reabre": "Reopened: the fix did not hold",
      "lc.motivo.tardia": "Late reopen by support",

      "arq.title": "Java architecture",
      "arq.lead": "JDBC without a framework: the CLI holds no SQL and no business rules. Services own the transaction, DAOs receive the Connection (unit of work) and any rule violation rolls the whole operation back.",
      "arq.cli.name": "CLI",
      "arq.cli.desc": "Role-based menus —employee, technician and administrator— to create, assign, transition, comment, rate and browse reports. It renders data and catches exceptions; it never decides rules.",
      "arq.service.name": "Services",
      "arq.service.desc": "TicketService owns permissions, the state machine, SLAs and auditing. Every mutation writes ticket + history inside one transaction: both land or neither does.",
      "arq.dao.name": "DAO / JDBC",
      "arq.dao.desc": "Unit-of-work pattern: writes take the open Connection, reads open their own. PreparedStatement everywhere, zero SQL string concatenation.",
      "arq.db.name": "MySQL 8",
      "arq.db.desc": "Views power the KPIs —queue, SLA breaches and average resolution per technician— with indexes on the hot paths and constraints at the data level.",
      "arq.packages": "Packages",

      "datos.title": "Sample data",
      "datos.lead": "48 tickets generated with a coherent lifecycle —creation, assignment, waiting states, resolution and closure— by tools/seed_generator.py: 185 history entries, 106 comments, ratings and real bcrypt hashes.",
      "datos.credentials": "Demo: every seeded user signs in with the password royaltown2026 (marta.sanchez@royaltown.local is an employee, laura.gomez@royaltown.local a technician and alejandro.ortiz@royaltown.local an administrator).",
      "datos.th.id": "ID",
      "datos.th.titulo": "Title",
      "datos.th.cat": "Category",
      "datos.th.prio": "Priority",
      "datos.th.estado": "Status",
      "datos.th.tecnico": "Technician",
      "datos.th.fecha": "Created",
      "datos.muestra": "Sample rows from the Tickets table",

      "sql.title": "Full SQL script",
      "sql.copy": "Copy script",
      "sql.copied": "Copied!",
      "sql.loading": "Loading schema.sql…",
      "sql.fallback": "Could not load the script; open it directly in the repository.",
      "sql.note": "Served from schema.sql, the same file Maven loads in the tests. Works on MySQL 8 and on H2 in MySQL mode.",

      "footer.text": "Portfolio project · RoyalTown helpdesk",
      "footer.repo": "View repository"
    }
  };

  function guardado() {
    try {
      return localStorage.getItem(CLAVE);
    } catch (e) {
      return null;
    }
  }

  function idiomaInicial() {
    var guard = guardado();
    if (guard && TEXTOS[guard]) {
      return guard;
    }
    return (navigator.language || "es").toLowerCase().indexOf("en") === 0 ? "en" : "es";
  }

  var actual = idiomaInicial();

  function traducir(clave) {
    return TEXTOS[actual][clave] || TEXTOS.es[clave] || null;
  }

  function aplicar(silencioso) {
    document.documentElement.lang = actual;

    document.querySelectorAll("[data-i18n]").forEach(function (nodo) {
      // data-loaded marca contenido ya cargado en tiempo de ejecucion (el
      // bloque SQL con schema.sql): no se pisa con la etiqueta de placeholder.
      if (nodo.hasAttribute("data-loaded")) {
        return;
      }
      var texto = traducir(nodo.getAttribute("data-i18n"));
      if (texto !== null) {
        nodo.textContent = texto;
      }
    });

    var atributos = [
      ["data-i18n-title", "title"],
      ["data-i18n-label", "aria-label"],
      ["data-i18n-placeholder", "placeholder"]
    ];
    atributos.forEach(function (par) {
      document.querySelectorAll("[" + par[0] + "]").forEach(function (nodo) {
        var texto = traducir(nodo.getAttribute(par[0]));
        if (texto !== null) {
          nodo.setAttribute(par[1], texto);
        }
      });
    });

    // El codigo EN/ES del boton lo gobierna CSS via html[lang] (capas
    // apiladas, como en el portfolio): aqui solo se actualiza la etiqueta.
    document.querySelectorAll("[data-lang-toggle]").forEach(function (boton) {
      boton.setAttribute("aria-label", traducir("toggle.lang"));
      boton.title = traducir("toggle.lang");
    });

    if (!silencioso) {
      window.dispatchEvent(new CustomEvent("idioma:cambiado", { detail: actual }));
    }
  }

  function cambiar() {
    actual = actual === "es" ? "en" : "es";
    try {
      localStorage.setItem(CLAVE, actual);
    } catch (e) {
      /* modo privado */
    }
    aplicar();
  }

  document.addEventListener("click", function (evento) {
    if (evento.target.closest("[data-lang-toggle]")) {
      cambiar();
    }
  });

  // API minima para el resto de scripts (script.js lee las etiquetas del
  // boton de copiar desde aqui en vez de duplicarlas).
  window.I18N = {
    t: function (clave) {
      return traducir(clave);
    },
    idioma: function () {
      return actual;
    },
    /** Vuelve a traducir sin re-animar los titulares (p. ej. tras cambiar
        dinamicamente la clave de un nodo). */
    refrescar: function () {
      aplicar(true);
    }
  };

  // Los scripts van al final del body: el DOM de la pagina ya esta parseado,
  // asi que se traduce ya. Si alguien los moviera a la cabecera, se vuelve a
  // traducir en DOMContentLoaded (silencioso: sin re-animar los titulares).
  aplicar();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      aplicar(true);
    });
  }
})();
