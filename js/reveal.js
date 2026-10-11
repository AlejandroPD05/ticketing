/* ==========================================================================
   reveal.js — entrada por scroll + contadores animados.

   Ambos usan el mismo IntersectionObserver: el contenido solo se oculta
   detras de html.js y nunca si el movimiento esta reducido (eso lo decide
   effects.css, que fuerza opacity:1).
   ========================================================================== */

(function () {
  "use strict";

  if (!window.FX) {
    return;
  }

  // --- Reveal ---------------------------------------------------------------
  var nodos = document.querySelectorAll("[data-reveal]");

  if (FX.animar()) {
    nodos.forEach(function (nodo, indice) {
      // Escalonado dentro de un mismo grupo de reveal.
      var grupo = nodo.getAttribute("data-reveal-group");
      if (grupo) {
        var anteriores = document.querySelectorAll('[data-reveal-group="' + grupo + '"]');
        var posicion = Array.prototype.indexOf.call(anteriores, nodo);
        nodo.style.setProperty("--reveal-delay", posicion * 80 + "ms");
      } else if (indice < 3) {
        nodo.style.setProperty("--reveal-delay", indice * 60 + "ms");
      }

      FX.alEntrar(nodo, function (el) {
        el.classList.add("is-visible");
      });
    });
  } else {
    nodos.forEach(function (nodo) {
      nodo.classList.add("is-visible");
    });
  }

  // --- Contadores -----------------------------------------------------------
  // Mismo easing/duración que Portfolio/js/effects/counters.js: salida
  // exponencial suave (~99.9% del objetivo al final) en 1100ms.
  function easeOutExpo(avance) {
    return avance >= 1 ? 1 : 1 - Math.pow(2, -10 * avance);
  }

  function animarContador(nodo) {
    var destino = parseFloat(nodo.getAttribute("data-count-to"));
    if (isNaN(destino)) {
      return;
    }
    var decimales = (nodo.getAttribute("data-count-to").split(".")[1] || "").length;
    var duracion = 1100;

    if (!FX.animar()) {
      nodo.textContent = destino.toFixed(decimales);
      return;
    }

    var inicio = null;
    function paso(marca) {
      if (inicio === null) {
        inicio = marca;
      }
      var avance = Math.min((marca - inicio) / duracion, 1);
      nodo.textContent = (destino * easeOutExpo(avance)).toFixed(decimales);
      if (avance < 1) {
        requestAnimationFrame(paso);
      } else {
        // El valor final se reescribe tal cual: no puede driftar.
        nodo.textContent = destino.toFixed(decimales);
      }
    }
    requestAnimationFrame(paso);
  }

  document.querySelectorAll("[data-count-to]").forEach(function (nodo) {
    nodo.textContent = "0";
    FX.alEntrar(nodo, animarContador, "0px 0px -20% 0px");
  });
})();
