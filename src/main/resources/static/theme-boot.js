// Loaded synchronously in <head> so the cached palette is applied before the first paint
// (otherwise every navigation flashes the default dark palette). theme.js takes over afterwards.
(function () {
    const LIGHT = ["AZUL", "PETROLEO", "AMEIXA"];
    const DARK = ["GRAFITE", "BORDO", "MEIA_NOITE"];
    let palette = "GRAFITE";
    try {
        const stored = localStorage.getItem("jamorder-palette");
        if (LIGHT.includes(stored) || DARK.includes(stored)) palette = stored;
        else if (localStorage.getItem("jamorder-theme") === "light") palette = "AZUL";
    } catch { /* storage unavailable: keep the default */ }
    document.documentElement.dataset.palette = palette;
    document.documentElement.classList.toggle("theme-light", LIGHT.includes(palette));
})();
