/* ==========================================================================
   theme.js — claro/oscuro con los mismos contratos que el portfolio:

     * Toda visita se abre en claro: el script inline de la cabecera ya pinto
       `light-mode` antes del primer frame y la preferencia del SO se ignora
       deliberadamente.
     * El modo oscuro es una decision de sesion: no se escribe en
       localStorage, la proxima visita vuelve a empezar en blanco.
     * El cambio cruza la pagina entera con document.startViewTransition
       (keyframes de base.css) cuando el navegador lo soporta y el usuario no
       pide movimiento reducido.
     * Al terminar el pintado se lanza "tema:cambiado" para que er.js repinta
       el diagrama con la paleta nueva.
   ========================================================================== */

(function () {
  "use strict";

  var raiz = document.documentElement;
  var actual = "light";

  function pintar(modo) {
    var oscuro = modo === "dark";

    var aplicar = function () {
      raiz.classList.toggle("dark-mode", oscuro);
      raiz.classList.toggle("light-mode", !oscuro);

      var meta = document.querySelector('meta[name="theme-color"]');
      if (meta) {
        meta.setAttribute("content", oscuro ? "#000000" : "#ffffff");
      }

      document.querySelectorAll("[data-theme-toggle]").forEach(function (boton) {
        boton.setAttribute("aria-pressed", String(oscuro));
      });

      window.dispatchEvent(new CustomEvent("tema:cambiado", { detail: modo }));
    };

    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var sinMovimiento = raiz.classList.contains("motion-off");

    if (typeof document.startViewTransition === "function" && !reduce && !sinMovimiento) {
      document.startViewTransition(aplicar);
    } else {
      aplicar();
    }
  }

  document.addEventListener("click", function (evento) {
    if (evento.target.closest && evento.target.closest("[data-theme-toggle]")) {
      actual = actual === "dark" ? "light" : "dark";
      pintar(actual);
    }
  });

  // Siempre se abre en claro; el estado del modulo ya esta sincronizado.
  pintar("light");
})();
