/* ==========================================================================
   er.js — pinta el diagrama entidad-relación con Mermaid (CDN).

   Si Mermaid no llega a cargar (sin red), el frame nunca marca data-rendered
   y se queda visible el texto alternativo de components.css. El diagrama se
   repinta al cambiar de tema para que los colores sigan los tokens.
   ========================================================================== */

(function () {
  "use strict";

  var marco = document.querySelector(".er-frame");
  var pre = document.querySelector(".er-frame .mermaid");
  if (!marco || !pre) {
    return;
  }

  // Codigo fuente del diagrama: tras el primer render <pre> pasa a contener SVG.
  var codigo = pre.textContent.trim();
  var contador = 0;

  /* En los diagramas ER, Mermaid rellena la caja de entidad con
     primaryColor, su borde con primaryBorderColor, el texto con
     primaryTextColor y las etiquetas de relación con secondaryColor y
     secondaryTextColor. */
  function variables(oscuro) {
    return {
      fontFamily: "IBM Plex Mono, monospace",
      fontSize: "13px",
      primaryColor: oscuro ? "#0c1f16" : "#eaf5ee",
      primaryBorderColor: oscuro ? "#6fffb0" : "#0b7a3c",
      primaryTextColor: oscuro ? "#ffffff" : "#0b2e1c",
      secondaryColor: oscuro ? "rgba(111, 255, 176, 0.16)" : "rgba(11, 122, 60, 0.10)",
      secondaryTextColor: oscuro ? "#cfe3d7" : "#2e4b3d",
      tertiaryColor: oscuro ? "#0c1f16" : "#eaf5ee",
      lineColor: oscuro ? "#2f6b4c" : "#2e4b3d",
      textColor: oscuro ? "#cfe3d7" : "#2e4b3d",
      background: oscuro ? "#000000" : "#ffffff"
    };
  }

  function pintar() {
    if (typeof mermaid === "undefined") {
      return; // sin red: se queda el fallback
    }
    var oscuro = document.documentElement.classList.contains("dark-mode");
    try {
      mermaid.initialize({
        startOnLoad: false,
        // "base" es imprescindible: los temas con nombre (neutral, dark…)
        // sobrescriben los themeVariables y las cajas se quedan en su color.
        theme: "base",
        themeVariables: variables(oscuro),
        er: { useMaxWidth: false }
      });
      var id = "er-diagrama-" + (++contador);
      var promesa = mermaid.render(id, codigo);
      if (promesa && typeof promesa.then === "function") {
        promesa.then(function (resultado) {
          pre.innerHTML = resultado.svg;
          marco.setAttribute("data-rendered", "true");
        }).catch(function () {
          /* se conserva el fallback */
        });
      }
    } catch {
      /* se conserva el fallback */
    }
  }

  if (document.readyState === "complete") {
    pintar();
  } else {
    // El script de Mermaid va con defer: `load` garantiza que ya esta definido.
    window.addEventListener("load", pintar);
  }

  // Repinta tras alternar el tema (theme.js lanza tema:cambiado tras pintar;
  // el pequeño margen deja que el view-transition capture el estado nuevo).
  window.addEventListener("tema:cambiado", function () {
    window.setTimeout(pintar, 80);
  });
})();
