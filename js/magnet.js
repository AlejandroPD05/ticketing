/* ==========================================================================
   magnet.js — atracción magnética de los controles primarios.

   Réplica de Portfolio/js/effects/magnet.js: `.btn` e `.icon-toggle` se
   resuelven con UN mouseover delegado en document y se cachean como
   "activo". Un solo pointermove hace todo el trabajo — y solo para ese
   elemento: el offset desde su centro lleva un translate en línea topeado
   en MAX_PULL px, suave gracias al `transition: transform ... ease-out` que
   `.is-magnetic` pinta en css/effects.css.

   Nada aquí llama preventDefault y solo se escriben transforms (el valor en
   línea se borra al salir) — así que los clics, los anillos :focus-visible,
   la selección de texto y los estilos hover de components.css siguen
   exactamente como antes. Puntero grueso o movimiento reducido: ni un
   listener.
   ========================================================================== */

(function () {
  "use strict";

  if (!window.FX || !FX.animar() || !FX.raton()) {
    return;
  }

  /** Elementos que atraen al puntero. */
  var SELECTOR = ".btn, .icon-toggle";
  /** Tira máxima en px en cualquier eje. */
  var MAX_PULL = 10;

  var activo = null;

  function soltar() {
    if (!activo) return;
    activo.classList.remove("is-magnetic");
    activo.style.transform = "";
    activo = null;
  }

  document.addEventListener("mouseover", function (evento) {
    var target =
      evento.target instanceof Element ? evento.target.closest(SELECTOR) : null;

    if (target === activo) return;

    soltar();

    if (target === null) return;

    activo = target;
    activo.classList.add("is-magnetic");
  });

  document.addEventListener("mouseout", function (evento) {
    if (!activo) return;

    var destino = evento.relatedTarget;
    if (destino instanceof Node && activo.contains(destino)) return; /* sigue dentro */

    soltar();
  });

  document.addEventListener("pointermove", function (evento) {
    if (!activo) return;
    if (!activo.isConnected) {
      soltar();
      return;
    }

    var rect = activo.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;

    var offsetX = clamp((evento.clientX - (rect.left + rect.width / 2)) / (rect.width / 2));
    var offsetY = clamp((evento.clientY - (rect.top + rect.height / 2)) / (rect.height / 2));

    activo.style.transform =
      "translate(" + (offsetX * MAX_PULL).toFixed(2) + "px, " +
      (offsetY * MAX_PULL).toFixed(2) + "px)";
  }, { passive: true });

  function clamp(valor) {
    return Math.min(1, Math.max(-1, valor));
  }

  document.documentElement.addEventListener("pointerleave", soltar);
})();
