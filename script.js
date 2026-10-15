/* ==========================================================================
   script.js — carga el script SQL real (schema.sql) en la pagina y lo copia
   al portapapeles.

   Ventaja frente a pegar 700 lineas en el HTML: la pagina siempre muestra el
   fichero que Maven ejecuta en los tests, asi que no puede desincronizarse.
   ========================================================================== */

(function () {
  "use strict";

  var bloque = document.querySelector("[data-sql-code]");
  var boton = document.querySelector("[data-copy]");
  if (!bloque) {
    return;
  }

  var etiquetaBoton = boton ? boton.querySelector("[data-copy-label]") : null;
  var nota = document.querySelector("[data-sql-note]");

  function textoActual() {
    return bloque.textContent || "";
  }

  fetch("schema.sql", { cache: "no-cache" })
    .then(function (respuesta) {
      if (!respuesta.ok) {
        throw new Error(respuesta.status);
      }
      return respuesta.text();
    })
    .then(function (sql) {
      bloque.textContent = sql;
      bloque.setAttribute("data-loaded", "true");
      window.dispatchEvent(new CustomEvent("sql:cargado"));
    })
    .catch(function () {
      // Se re-apunta la clave y se refresca: asi el texto sigue al idioma.
      bloque.textContent = "-- schema.sql no disponible.";
      if (nota) {
        nota.setAttribute("data-i18n", "sql.fallback");
        if (window.I18N) {
          I18N.refrescar();
        }
      }
      if (boton) {
        boton.disabled = true;
      }
    });

  if (!boton) {
    return;
  }

  boton.addEventListener("click", function () {
    var texto = textoActual();
    if (!texto || boton.disabled) {
      return;
    }

    function confirmado() {
      var t = window.I18N ? I18N.t.bind(I18N) : function (k) {
        return k === "sql.copied" ? "¡Copiado!" : "Copiar script";
      };

      boton.classList.add("copied");
      if (etiquetaBoton) {
        etiquetaBoton.textContent = t("sql.copied");
      }
      window.setTimeout(function () {
        boton.classList.remove("copied");
        if (etiquetaBoton) {
          etiquetaBoton.setAttribute("data-i18n", "sql.copy");
          etiquetaBoton.textContent = t("sql.copy");
        }
      }, 1800);
    }

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(texto).then(confirmado).catch(function () {
        fallbackCopia(texto, confirmado);
      });
    } else {
      fallbackCopia(texto, confirmado);
    }
  });

  function fallbackCopia(texto, alListo) {
    var area = document.createElement("textarea");
    area.value = texto;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    try {
      document.execCommand("copy");
      alListo();
    } catch {
      /* el navegador bloqueo la copia manual */
    }
    document.body.removeChild(area);
  }
})();
