/* ==========================================================================
   cursor.js — puntero personalizado (solo punteros finos). Réplica del
   Portfolio/js/effects/cursor.js:

     * Dos capas `position: fixed` (.fx-cursor-dot / .fx-cursor-ring) que
       mueve UN solo bucle rAF: el punto se escribe directo desde la ultima
       posicion del puntero (cero lag) y el anillo hace lerp hacia el con
       RING_EASE por frame, para un tacto con retardo y peso.
     * `html.has-custom-cursor` oculta el puntero nativo (los campos de texto
       conservan su cursor de insercion, lo gestiona effects.css).
     * El hover se delega: mouseover/mouseout + closest() resuelven
       a, button, input, [role="button"] y .tiltable; el anillo pasa a halo
       punteado y crece, y ambas capas encogen al pulsar.
     * Al salir del documento se oculta todo; al volver, el anillo se engancha
       al puntero para no entrar barriendo desde una posicion vieja.
     * El bucle se aparca en cuanto el anillo alcanza al puntero: un puntero
       quieto no consume frames.
     * Puntero grueso o movimiento reducido: ni un solo efecto secundario.
   ========================================================================== */

(function () {
  "use strict";

  if (!window.FX || !FX.animar() || !FX.raton()) {
    return;
  }

  /** Lerp por frame (~0.15 se lee suave a 60fps). */
  var RING_EASE = 0.15;
  /** Escala del anillo sobre un elemento interactivo. */
  var HOVER_SCALE = 1.6;
  /** Ambas capas encogen con el boton pulsado. */
  var PRESS_SCALE_RING = 0.72;
  var PRESS_SCALE_DOT = 0.6;
  var REST_SCALE = 1;
  /** Deltas por debajo de los cuales el anillo se considera quieto. */
  var SETTLE_DISTANCE = 0.05;
  var SETTLE_SCALE = 0.001;
  var HOVER_SELECTOR = 'a, button, input, [role="button"], .tiltable';

  var punto = document.createElement("div");
  punto.className = "fx-cursor-dot";
  punto.setAttribute("aria-hidden", "true");

  var anillo = document.createElement("div");
  anillo.className = "fx-cursor-ring";
  anillo.setAttribute("aria-hidden", "true");

  document.documentElement.classList.add("has-custom-cursor");
  document.body.append(punto, anillo);

  /* Estado del bucle: lo escriben los listeners, lo consume UN rAF. */
  var pointerX = 0;
  var pointerY = 0;
  var ringX = 0;
  var ringY = 0;
  var ringScale = REST_SCALE;
  var dotScale = REST_SCALE;
  var ringTarget = REST_SCALE;
  var dotTarget = REST_SCALE;
  var shown = false;
  var pressed = false;
  var hovering = false;
  var frameId = null;

  /** Un frame: suaviza el anillo, escribe ambos transforms, se aparca al asentar. */
  function frame() {
    frameId = null;

    ringX += (pointerX - ringX) * RING_EASE;
    ringY += (pointerY - ringY) * RING_EASE;
    ringScale += (ringTarget - ringScale) * RING_EASE;
    dotScale += (dotTarget - dotScale) * RING_EASE;

    punto.style.transform =
      "translate3d(" + pointerX + "px," + pointerY + "px,0) scale(" + dotScale + ")";
    anillo.style.transform =
      "translate3d(" + ringX + "px," + ringY + "px,0) scale(" + ringScale + ")";

    var asentado =
      Math.abs(pointerX - ringX) < SETTLE_DISTANCE &&
      Math.abs(pointerY - ringY) < SETTLE_DISTANCE &&
      Math.abs(ringTarget - ringScale) < SETTLE_SCALE &&
      Math.abs(dotTarget - dotScale) < SETTLE_SCALE;

    if (!asentado) frameId = window.requestAnimationFrame(frame);
  }

  /** Como mucho un frame en vuelo, pese a los eventos que lo pidan. */
  function schedule() {
    if (frameId === null) frameId = window.requestAnimationFrame(frame);
  }

  /** Sigue al puntero; revela las capas a la primera vista, enganchadas a el. */
  function show(x, y) {
    pointerX = x;
    pointerY = y;

    if (!shown) {
      shown = true;
      ringX = x;
      ringY = y; /* engancha: nunca barre desde una posicion vieja */
      punto.classList.add("is-visible");
      anillo.classList.add("is-visible");
    }

    schedule();
  }

  /** El puntero salio del documento: oculta todo y suelta hover/pulsacion. */
  function hide() {
    shown = false;
    pressed = false;
    hovering = false;
    punto.classList.remove("is-visible", "is-pressed");
    anillo.classList.remove("is-visible", "is-pressed", "is-hovering");
    dotTarget = REST_SCALE;
    ringTarget = REST_SCALE;
    schedule(); /* asienta las escalas detras del fundido */
  }

  function setHovering(next) {
    if (next === hovering) return;
    hovering = next;
    anillo.classList.toggle("is-hovering", next);
    ringTarget = next ? HOVER_SCALE : pressed ? PRESS_SCALE_RING : REST_SCALE;
    schedule();
  }

  document.addEventListener("pointermove", function (evento) {
    if (evento.pointerType && evento.pointerType !== "mouse") return;
    show(evento.clientX, evento.clientY);
  }, { passive: true });

  document.addEventListener("pointerdown", function () {
    if (pressed) return;
    pressed = true;
    punto.classList.add("is-pressed");
    anillo.classList.add("is-pressed");
    dotTarget = PRESS_SCALE_DOT;
    ringTarget = PRESS_SCALE_RING;
    schedule();
  }, { passive: true });

  document.addEventListener("pointerup", function () {
    if (!pressed) return;
    pressed = false;
    punto.classList.remove("is-pressed");
    anillo.classList.remove("is-pressed");
    dotTarget = REST_SCALE;
    ringTarget = hovering ? HOVER_SCALE : REST_SCALE;
    schedule();
  }, { passive: true });

  document.addEventListener("mouseover", function (evento) {
    var objetivo = evento.target instanceof Element ? evento.target.closest(HOVER_SELECTOR) : null;
    setHovering(Boolean(objetivo));
  });

  document.addEventListener("mouseout", function (evento) {
    var origen = evento.target instanceof Element ? evento.target.closest(HOVER_SELECTOR) : null;
    if (!origen) return;

    var destino = evento.relatedTarget;
    if (destino instanceof Element && origen.contains(destino)) return; /* sigue dentro */

    setHovering(false);
  });

  document.documentElement.addEventListener("pointerenter", function (evento) {
    show(evento.clientX, evento.clientY);
  });

  document.documentElement.addEventListener("pointerleave", hide);
})();
