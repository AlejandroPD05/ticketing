/* ==========================================================================
   setup.js — contratos de arranque compartidos por todos los efectos.

   Deja en <html> las clases que gobiernan la animacion (el script inline de
   la cabecera ya pinto force-motion / motion-off antes del primer frame):

     .js            hay JavaScript (los efectos solo se ocultan si la hay)
     .force-motion  ?motion=on  -> fuerza animaciones (escape de demo)
     .motion-off    ?motion=off -> apaga todos los efectos
     .touch         el dispositivo no tiene puntero de raton (sin cursor/tilt)

   Y publica window.FX con las comprobaciones reutilizables.
   ========================================================================== */

(function () {
  "use strict";

  var raiz = document.documentElement;
  raiz.classList.add("js");

  var sinPuntero = window.matchMedia("(hover: none), (pointer: coarse)");
  function marcarPuntero() {
    raiz.classList.toggle("touch", sinPuntero.matches);
  }
  marcarPuntero();
  if (sinPuntero.addEventListener) {
    sinPuntero.addEventListener("change", marcarPuntero);
  }

  var reduce = window.matchMedia("(prefers-reduced-motion: reduce)");

  window.FX = {
    /** true si los efectos de movimiento deben ejecutarse. */
    animar: function () {
      if (raiz.classList.contains("motion-off")) {
        return false;
      }
      if (raiz.classList.contains("force-motion")) {
        return true;
      }
      return !reduce.matches;
    },
    /** true si hay puntero de raton real (no tilde, no dedo). */
    raton: function () {
      return !raiz.classList.contains("touch");
    },
    /** Ejecuta `fn` la primera vez que el elemento entra (o ya esta) en
        pantalla: lo que ya se ve al cargar no espera al observer, porque el
        rootMargin negativo sirve para disparar antes de entrar, no para
        esconder contenido ya visible. */
    alEntrar: function (elemento, fn, margen) {
      var rect = elemento.getBoundingClientRect();
      if (rect.top < window.innerHeight && rect.bottom > 0) {
        fn(elemento);
        return;
      }
      if (!("IntersectionObserver" in window)) {
        fn(elemento);
        return;
      }
      var obs = new IntersectionObserver(function (entradas) {
        entradas.forEach(function (entrada) {
          if (entrada.isIntersecting) {
            fn(entrada.target);
            obs.unobserve(entrada.target);
          }
        });
      }, { rootMargin: margen || "0px 0px -12% 0px", threshold: 0.12 });
      obs.observe(elemento);
    }
  };
})();
