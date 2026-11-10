// Shared palette handling for every page.
// The palette is saved on the user (PUT /api/auth/me/palette) and cached in localStorage
// so pages, including the login page, open with the right colors before the API answers.
(function () {
    const STORAGE_KEY = "jamorder-palette";
    const LEGACY_THEME_KEY = "jamorder-theme";

    // swatch: background, surface, accent
    const PALETTES = [
        { id: "AZUL", name: "Azul", mode: "light", swatch: ["#f5f6f8", "#ffffff", "#1f5fae"] },
        { id: "PETROLEO", name: "Petróleo", mode: "light", swatch: ["#f3f6f6", "#ffffff", "#0e6b66"] },
        { id: "AMEIXA", name: "Ameixa", mode: "light", swatch: ["#f6f4f7", "#ffffff", "#6b3f8f"] },
        { id: "GRAFITE", name: "Grafite", mode: "dark", swatch: ["#161b22", "#20262f", "#2f6fbf"] },
        { id: "BORDO", name: "Bordô", mode: "dark", swatch: ["#1a1315", "#261a1d", "#8e2c43"] },
        { id: "MEIA_NOITE", name: "Meia-noite", mode: "dark", swatch: ["#13141f", "#1c1e2d", "#5452c9"] },
    ];
    const DEFAULT_PALETTE = "GRAFITE";

    let current = DEFAULT_PALETTE;
    let pickerButton = null;
    let pickerMenu = null;

    function findPalette(id) { return PALETTES.find(palette => palette.id === id); }

    function readStored() {
        try {
            const stored = localStorage.getItem(STORAGE_KEY);
            if (findPalette(stored)) return stored;
            // Users who only used the old light/dark toggle keep what they had.
            return localStorage.getItem(LEGACY_THEME_KEY) === "light" ? "AZUL" : DEFAULT_PALETTE;
        } catch {
            return DEFAULT_PALETTE;
        }
    }

    function apply(id) {
        const palette = findPalette(id) ?? findPalette(DEFAULT_PALETTE);
        current = palette.id;
        document.documentElement.classList.toggle("theme-light", palette.mode === "light");
        document.documentElement.dataset.palette = palette.id;
        try { localStorage.setItem(STORAGE_KEY, palette.id); } catch { /* cache is optional */ }
        renderPicker();
    }

    async function save(id) {
        try {
            const csrfResponse = await fetch("/api/csrf");
            const csrf = await csrfResponse.json();
            const response = await fetch("/api/auth/me/palette", {
                method: "PUT",
                headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
                body: JSON.stringify({ palette: id }),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
        } catch (error) {
            console.warn("Não foi possível salvar a paleta no usuário.", error);
        }
    }

    function swatchElement(palette) {
        const swatch = document.createElement("span");
        swatch.className = "palette-swatch";
        swatch.setAttribute("aria-hidden", "true");
        for (const color of palette.swatch) {
            const part = document.createElement("i");
            part.style.background = color;
            swatch.append(part);
        }
        return swatch;
    }

    function buildPicker(container) {
        pickerButton = document.createElement("button");
        pickerButton.type = "button";
        pickerButton.className = "palette-button";
        pickerButton.setAttribute("popovertarget", "palette-menu");
        pickerButton.setAttribute("aria-haspopup", "menu");
        const dot = document.createElement("span");
        dot.className = "palette-dot";
        pickerButton.append(dot);

        pickerMenu = document.createElement("div");
        pickerMenu.id = "palette-menu";
        pickerMenu.className = "palette-menu";
        pickerMenu.setAttribute("popover", "");
        pickerMenu.setAttribute("role", "menu");

        for (const [mode, title] of [["light", "Claras"], ["dark", "Escuras"]]) {
            const heading = document.createElement("h3");
            heading.textContent = title;
            const options = document.createElement("div");
            options.className = "palette-options";
            for (const palette of PALETTES.filter(item => item.mode === mode)) {
                const option = document.createElement("button");
                option.type = "button";
                option.className = "palette-option";
                option.setAttribute("role", "menuitemradio");
                option.dataset.palette = palette.id;
                option.append(swatchElement(palette), document.createTextNode(palette.name));
                option.addEventListener("click", () => {
                    pickerMenu.hidePopover();
                    if (palette.id === current) return;
                    apply(palette.id);
                    save(palette.id);
                });
                options.append(option);
            }
            pickerMenu.append(heading, options);
        }

        // Place the menu under the button, aligned to its right edge.
        pickerMenu.addEventListener("toggle", event => {
            if (event.newState !== "open") return;
            const rect = pickerButton.getBoundingClientRect();
            pickerMenu.style.top = `${rect.bottom + 6}px`;
            pickerMenu.style.left = `${Math.max(8, rect.right - pickerMenu.offsetWidth)}px`;
        });

        container.append(pickerButton, pickerMenu);
        renderPicker();
    }

    function renderPicker() {
        if (!pickerButton) return;
        const palette = findPalette(current);
        pickerButton.querySelector(".palette-dot").style.background = palette.swatch[2];
        const label = `Paleta de cores: ${palette.name}`;
        pickerButton.setAttribute("aria-label", label);
        pickerButton.title = label;
        for (const option of pickerMenu.querySelectorAll(".palette-option")) {
            option.setAttribute("aria-checked", String(option.dataset.palette === current));
        }
    }

    async function syncFromUser() {
        try {
            const response = await fetch("/api/auth/me");
            if (!response.ok) return;
            const user = await response.json();
            if (user.palette && user.palette !== current) apply(user.palette);
        } catch { /* keep the cached palette */ }
    }

    apply(readStored());
    const container = document.querySelector("#palette-picker");
    if (container) {
        buildPicker(container);
        syncFromUser();
    }

    window.JamTheme = { apply };
})();
