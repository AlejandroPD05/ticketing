/* ==========================================================================
   tilt.js — inclinación 3D + destello (glare) que sigue al puntero.

   Réplica de Portfolio/js/effects/tilt.js: UN pointermove delegado en
   document resuelve el `.tiltable` con closest() (sin listeners por
   tarjeta), el elemento activo lo sigue un solo bucle rAF que en cada frame
   lee primero su rect (las escrituras solo tocan transform, así que la
   lectura barata mantiene el tilt honesto aunque la página ruede bajo un
   puntero quieto), suaviza la rotación hacia el objetivo y escribe
   `transform` + `--mx`/`--my` en un mismo lote.

   `is-tilting` (css/effects.css) entrega will-change, el salto de z-index y
   el ::after del glare; el transform va en línea como
   `perspective(900px) rotateX(..) rotateY(..) scale(1.015)` con tope en
   MAX_GRADOS. Nunca se llama preventDefault/stopPropagation, así que los
   enlaces dentro de las tarjetas siguen vivos; `pointerleave` en <html>, un
   guard isConnected y el chequeo fuera de rango resetean limpio. Puntero
   grueso o movimiento reducido: ni un efecto.
   ========================================================================== */

(function () {
  "use strict";

  if (!window.FX || !FX.animar() || !FX.raton()) {
    return;
  }

  /** Superficies que se inclinan. */
  var TILT_SELECTOR = ".tiltable";
  /** Grados máximos de giro en cada eje. */
  var MAX_GRADOS = 7;
  /** Perspectiva embebida en el string del transform, en px. */
  var PERSPECTIVA = 900;
  /** Escala leve que vende el levantamiento sin mover el layout. */
  var HOVER_SCALE = 1.015;
  /** Suavizado exponencial por frame (~0.15 se lee sedoso a 60fps). */
  var SMOOTHING = 0.15;
  /** Offset del puntero (fracción del rect) tolerado antes de dar por salido. */
  var LEAVE_TOLERANCE = 0.02;

  /** El elemento bajo el puntero, si lo hay. */
  var activo = null;
  /** Última posición del puntero, en coordenadas de viewport. */
  var pointerX = 0;
  var pointerY = 0;
  /** Valores escritos, que se acercan al objetivo derivado del puntero. */
  var rotateX = 0;
  var rotateY = 0;
  var scale = 1;
  /** Últimos valores escritos: los repetidos idénticos se saltan. */
  var lastRotateX = null;
  var lastRotateY = null;
  var lastScale = null;
  var lastMx = "";
  var lastMy = "";
  var frameId = null;

  function clamp(valor, min, max) {
    return Math.min(max, Math.max(min, valor));
  }

  function round(valor) {
    return Math.round(valor * 100) / 100;
  }

  /** Reset completo: cancela el frame y devuelve la tarjeta al CSS. */
  function reset() {
    if (frameId !== null) {
      window.cancelAnimationFrame(frameId);
      frameId = null;
    }
    if (!activo) return;

    activo.classList.remove("is-tilting");
    activo.style.transform = "";
    activo.style.removeProperty("--mx");
    activo.style.removeProperty("--my");
    activo = null;

    rotateX = 0;
    rotateY = 0;
    scale = 1;
    lastRotateX = null;
    lastRotateY = null;
    lastScale = null;
    lastMx = "";
    lastMy = "";
  }

  /** Como mucho un frame en vuelo mientras haya tarjeta activa. */
  function schedule() {
    if (activo === null || frameId !== null) return;
    frameId = window.requestAnimationFrame(frame);
  }

  /** Escribe el transform y las coordenadas del glare solo si cambiaron. */
  function write(px, py) {
    var rx = round(rotateX);
    var ry = round(rotateY);
    var nextScale = round(scale);

    if (rx !== lastRotateX || ry !== lastRotateY || nextScale !== lastScale) {
      lastRotateX = rx;
      lastRotateY = ry;
      lastScale = nextScale;
      activo.style.transform =
        "perspective(" + PERSPECTIVA + "px) rotateX(" + rx +
        "deg) rotateY(" + ry + "deg) scale(" + nextScale + ")";
    }

    var mx = round(px * 100) + "%";
    var my = round(py * 100) + "%";

    if (mx !== lastMx) {
      lastMx = mx;
      activo.style.setProperty("--mx", mx);
    }
    if (my !== lastMy) {
      lastMy = my;
      activo.style.setProperty("--my", my);
    }
  }

  /** Un frame: medir primero, suavizar después, escribir al final. */
  function frame() {
    frameId = null;
    if (activo === null) return;

    if (!activo.isConnected) {
      reset();
      return;
    }

    var rect = activo.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      schedule();
      return;
    }

    var rawX = (pointerX - rect.left) / rect.width;
    var rawY = (pointerY - rect.top) / rect.height;

    /* Red de seguridad del scroll: si la tarjeta se fue de debajo del
       puntero sin llegar más pointermove, esto la resetea igual. */
    if (
      rawX < -LEAVE_TOLERANCE || rawX > 1 + LEAVE_TOLERANCE ||
      rawY < -LEAVE_TOLERANCE || rawY > 1 + LEAVE_TOLERANCE
    ) {
      reset();
      return;
    }

    var px = clamp(rawX, 0, 1);
    var py = clamp(rawY, 0, 1);

    /* La cara gira hacia el puntero: encima del centro echa el techo atrás,
       a la derecha del centro gira el canto derecho lejos. */
    rotateX += ((0.5 - py) * 2 * MAX_GRADOS - rotateX) * SMOOTHING;
    rotateY += ((px - 0.5) * 2 * MAX_GRADOS - rotateY) * SMOOTHING;
    scale += (HOVER_SCALE - scale) * SMOOTHING;

    write(px, py);
    schedule();
  }

  function onPointerMove(evento) {
    pointerX = evento.clientX;
    pointerY = evento.clientY;

    var target =
      evento.target instanceof Element ? evento.target.closest(TILT_SELECTOR) : null;

    if (target === activo) {
      schedule();
      return;
    }

    reset(); /* salir de una tarjeta (o reentrar en mitad del reset) empieza limpio */

    if (target === null) return;

    activo = target;
    activo.classList.add("is-tilting");
    schedule();
  }

  document.addEventListener("pointermove", onPointerMove, { passive: true });
  document.documentElement.addEventListener("pointerleave", reset);
})();
