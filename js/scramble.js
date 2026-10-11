/* ==========================================================================
   scramble.js — decodificación de titular estilo terminal.

   Réplica del algoritmo de Portfolio/js/effects/scramble.js: la primera vez
   que un `[data-scramble]` entra en pantalla su copia se reconstruye
   carácter a carácter con glifos de terminal aleatorios, resolviendo de
   izquierda a derecha con un poco de jitter por carácter.

   Solo se toca el textContent, así que el guion `_` que pinta
   `.section-title::after` nunca entra en la animación, y al terminar se
   escribe la cadena original tal cual: el resultado es idéntico al texto
   que había al empezar (los cambios de i18n se recogen porque el texto se
   lee fresco en cada arranque, vía textContent).

   Triggers: primera vez visible (FX.alEntrar), pasar el ratón por encima y
   el evento `idioma:cambiado`. Con movimiento reducido solo se escribe el
   texto final, sin ruido.
   ========================================================================== */

(function () {
  "use strict";

  if (!window.FX) {
    return;
  }

  /** Poza de glifos mientras un carácter sigue resolviéndose. */
  var GLYPHS = "01<>/\\{}[]#*+-=_";

  /** Techo duro de una pasada de decodificación — dentro del presupuesto 600–900ms. */
  var DURATION_MS = 800;
  /** Ventana en la que los caracteres pueden resolverse (de izquierda a derecha). */
  var RESOLVE_SPAN_MS = 560;
  /** Aleatoriedad extra por carácter encima de la ventana de resolución. */
  var JITTER_MS = 200;

  /** Elementos ya animados por la primera entrada. */
  var vistos = new WeakSet();

  /** Decodifica un elemento en su sitio con un único bucle rAF. */
  function decodificar(el) {
    var textoFinal = el.textContent;
    var chars = Array.from(textoFinal);
    if (chars.length === 0) return;

    if (el._scramble) {
      window.cancelAnimationFrame(el._scramble);
      el._scramble = null;
    }

    if (!FX.animar()) {
      el.textContent = textoFinal;
      return;
    }

    var inicio = null;

    var resolveAt = chars.map(function (char, index) {
      /* Los huecos de palabra se quedan fijos para que la línea mantenga su
         forma mientras se decodifica. */
      if (char.trim() === "") return 0;

      var spread = chars.length > 1 ? index / (chars.length - 1) : 1;
      return spread * RESOLVE_SPAN_MS + Math.random() * JITTER_MS;
    });

    function frame(marca) {
      el._scramble = null;

      if (inicio === null) {
        inicio = marca;
      }
      var elapsed = marca - inicio;

      if (elapsed >= DURATION_MS) {
        el.textContent = textoFinal;
        return;
      }

      var output = "";
      var pending = false;

      for (var i = 0; i < chars.length; i++) {
        if (elapsed >= resolveAt[i]) {
          output += chars[i];
          continue;
        }

        pending = true;
        output += GLYPHS.charAt(Math.floor(Math.random() * GLYPHS.length));
      }

      if (!pending) {
        el.textContent = textoFinal;
        return;
      }

      el.textContent = output;
      el._scramble = window.requestAnimationFrame(frame);
    }

    el._scramble = window.requestAnimationFrame(frame);
  }

  var nodos = document.querySelectorAll("[data-scramble]");

  nodos.forEach(function (nodo) {
    nodo.classList.add("scramble");

    /* Primera vez visible (FX.alEntrar ya dispara al entrar, incluso si el
       elemento estaba en pantalla desde el arranque). */
    FX.alEntrar(nodo, function (el) {
      if (vistos.has(el)) return;
      vistos.add(el);
      decodificar(el);
    });

    if (FX.raton()) {
      nodo.addEventListener("pointerenter", function () {
        decodificar(nodo);
      });
    }
  });

  // Al cambiar de idioma, i18n ha escrito el texto nuevo: lo re-animamos.
  window.addEventListener("idioma:cambiado", function () {
    nodos.forEach(function (nodo) {
      decodificar(nodo);
    });
  });
})();
