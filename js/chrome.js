/* ==========================================================================
   chrome.js — cromo de página que reacciona al scroll.

   Réplica de Portfolio/js/effects/chrome.js: UN handler de scroll con
   throttle rAF mueve tres piezas de feedback:

     a) la barra de progreso pegada al borde inferior de la cabecera fija
        (escrituras solo de transform),
     b) auto-hide de la cabecera: bajar de forma decidida la esconde,
        subir la devuelve — nunca con el foco de teclado dentro de la
        cabecera,
     c) `.is-scrolled` en la cabecera para un glass más marcado.

   Además flota el copy del hero (`translate3d` a 0.16 de la velocidad de
   scroll + fundido) mientras el hero está en pantalla y lo limpia después.

   Dentro del frame solo se lee la posición de scroll: la altura del
   documento se mide en init, resize, load y por ResizeObserver, así que
   escribir estilos nunca fuerza reflow en el mismo frame (sin layout
   thrash). Sin movimiento reducido no hay auto-hide ni parallax.
   ========================================================================== */

(function () {
  "use strict";

  if (!window.FX) {
    return;
  }

  var cabecera = document.querySelector(".site-header");
  if (!cabecera) return;

  var progreso = document.querySelector(".scroll-progress");
  var relleno = progreso ? progreso.querySelector("span") : null;

  var autoHide = FX.animar();
  var headerHeight = autoHide ? readHeaderHeight() : 0;

  /* Profundidad opcional del hero. */
  var hero = document.querySelector(".hero");
  var heroInner = document.querySelector(".hero-inner");
  var PARALLAX_FACTOR = 0.16;
  var parallaxCleared = true;

  var SCROLLED_OFFSET = 8;
  var HIDE_THRESHOLD = 6;
  var PROGRESS_EPSILON = 2;
  var FALLBACK_HEADER_H = 72;

  var scrollRange = 1;
  var needsMeasure = true;
  var ticking = false;
  var lastRatio = -1;

  /* Estado de hide/show: runDirection/runStart marcan el punto de giro del
     gesto actual para que 6px de temblor nunca volteen la cabecera. */
  var hidden = false;
  var prevY = window.scrollY || window.pageYOffset || 0;
  var runDirection = 0;
  var runStart = prevY;

  /** Valor de --header-h en px (72 por defecto). */
  function readHeaderHeight() {
    var raw = window
      .getComputedStyle(document.documentElement)
      .getPropertyValue("--header-h");
    var parsed = parseInt(raw, 10);

    return isNaN(parsed) ? FALLBACK_HEADER_H : parsed;
  }

  /** Cachea la distancia scrolleable: scrollHeight no se lee en mitad del loop. */
  function measure() {
    var root = document.documentElement;
    scrollRange = Math.max(1, root.scrollHeight - root.clientHeight);
  }

  /** La cabecera se queda quieta si el foco del teclado está dentro de ella. */
  function isPinned() {
    var activo = document.activeElement;
    if (!activo || !cabecera.contains(activo)) return false;

    try {
      /* Los clics de ratón no casan con :focus-visible: no anclan la barra. */
      return activo.matches(":focus-visible");
    } catch {
      /* Selector no soportado: se queda visible por seguridad. */
      return true;
    }
  }

  function setHidden(next) {
    if (hidden === next) return;
    hidden = next;
    cabecera.classList.toggle("is-hidden", next);
  }

  function updateHeaderVisibility(y) {
    /* Justo bajo la cabecera siempre está visible, y el gesto se reinicia. */
    if (y < headerHeight) {
      setHidden(false);
      runDirection = 0;
      runStart = y;
      prevY = y;
      return;
    }

    if (y !== prevY) {
      var direction = y > prevY ? 1 : -1;
      if (direction !== runDirection) {
        runDirection = direction;
        runStart = prevY;
      }
    }

    /* Anclado: re-ancorar para que cerrar el foco no esconda la barra luego. */
    if (isPinned()) {
      runStart = y;
      prevY = y;
      return;
    }

    if (runDirection > 0 && y - runStart >= HIDE_THRESHOLD) {
      setHidden(true);
    } else if (runDirection < 0 && runStart - y >= HIDE_THRESHOLD) {
      setHidden(false);
    }

    prevY = y;
  }

  /** Pinta el relleno de progreso (solo transform) y su estado de visibilidad. */
  function paintProgress(y) {
    if (!progreso || !relleno) return;

    var ratio = Math.min(1, Math.max(0, y / scrollRange));

    if (ratio !== lastRatio) {
      lastRatio = ratio;
      relleno.style.transform = "scaleX(" + ratio.toFixed(4) + ")";
    }

    progreso.classList.toggle("is-active", y > PROGRESS_EPSILON);
  }

  /** Flota el copy del hero mientras está en pantalla; lo limpia después. */
  function paintParallax(y) {
    if (!autoHide || !hero || !heroInner) return;

    var height = hero.offsetHeight || 1;

    if (y >= height) {
      if (parallaxCleared) return;
      parallaxCleared = true;
      heroInner.style.transform = "";
      heroInner.style.opacity = "";
      return;
    }

    parallaxCleared = false;
    heroInner.style.transform = "translate3d(0, " + (y * PARALLAX_FACTOR).toFixed(1) + "px, 0)";
    heroInner.style.opacity = String(Math.max(0, 1 - (y / height) * 1.15).toFixed(3));
  }

  function update() {
    if (needsMeasure) {
      needsMeasure = false;
      measure();
    }

    /* Lee la posición de scroll una sola vez por frame, antes de escribir. */
    var y = window.scrollY || window.pageYOffset || 0;

    paintProgress(y);
    paintParallax(y);
    cabecera.classList.toggle("is-scrolled", y > SCROLLED_OFFSET);

    if (autoHide) updateHeaderVisibility(y);
  }

  function schedule() {
    if (ticking) return;
    ticking = true;

    window.requestAnimationFrame(function () {
      ticking = false;
      update();
    });
  }

  function invalidate() {
    needsMeasure = true;
    schedule();
  }

  /* Primer pintado: refleja la posición actual (restauraciones incluidas). */
  update();

  window.addEventListener("scroll", schedule, { passive: true });
  window.addEventListener("resize", invalidate);
  window.addEventListener("load", invalidate);

  /* El contenido i18n cambia la altura de la página sin resize de ventana:
     re-mide desde el observer para que el ratio nunca envejezca. Nuestras
     escrituras son transforms y clases, así que esto nunca puede loop. */
  if ("ResizeObserver" in window) {
    var observer = new ResizeObserver(invalidate);
    observer.observe(document.documentElement);
  }
})();
