// Puente entre el reproductor OFICIAL de YouTube (página /embed) y la app.
// Solo usa los mismos métodos públicos del reproductor (playVideo, pauseVideo, seekTo,
// getCurrentTime...). No toca el audio ni el video.
(function () {
  var port = browser.runtime.connectNative("rutamusica");
  var lastState = -99, lastErr = "", autoplayTries = 0;

  function player() {
    var el = document.getElementById("movie_player");
    return el ? el.wrappedJSObject : null;
  }

  port.onMessage.addListener(function (m) {
    var p = player();
    if (!p) return;
    try {
      if (m.cmd === "play") p.playVideo();
      else if (m.cmd === "pause") p.pauseVideo();
      else if (m.cmd === "seek") p.seekTo(m.t, true);
    } catch (e) {}
  });

  function checkError() {
    var el = document.querySelector(".ytp-error");
    if (!el) return;
    var style = window.getComputedStyle(el);
    if (style.display === "none" || style.visibility === "hidden") return;
    var txt = (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim();
    if (txt && txt !== lastErr) {
      lastErr = txt;
      port.postMessage({ type: "error", text: txt.substring(0, 200) });
    }
  }

  setInterval(function () {
    var p = player();
    if (!p || typeof p.getPlayerState !== "function") { checkError(); return; }
    var st = -1;
    try { st = p.getPlayerState(); } catch (e) {}
    if (st !== lastState) {
      lastState = st;
      port.postMessage({ type: "state", s: st });
    }
    // Arranque automático (la app solo carga un video cuando la persona eligió una canción).
    if ((st === -1 || st === 5) && autoplayTries < 10) {
      autoplayTries++;
      try { p.playVideo(); } catch (e) {}
    }
    try {
      port.postMessage({ type: "time", t: p.getCurrentTime() || 0, d: p.getDuration() || 0 });
    } catch (e) {}
    checkError();
  }, 250);

  port.postMessage({ type: "ready" });
})();
