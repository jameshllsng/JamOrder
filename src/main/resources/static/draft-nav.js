// Draft counter on the "Requisições" sidebar item.
// The requisitions page (app.js) calls JamDraftNav.render() with what it already loaded;
// other pages fetch the list themselves.
(function () {
    const DRAFT_DANGER_DAYS = 3;

    function daysSince(value) {
        const since = Date.parse(`${value}T00:00:00Z`);
        const now = new Date();
        return Math.floor((Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) - since) / 86_400_000);
    }

    /** Drafts plus the age of the oldest one, in days. */
    function summarize(drafts) {
        const oldestDays = drafts.reduce((oldest, draft) => Math.max(oldest, daysSince(draft.requestedOn)), 0);
        return { count: drafts.length, oldestDays, overdue: drafts.length > 0 && oldestDays > DRAFT_DANGER_DAYS };
    }

    function render({ count, overdue }) {
        const badge = document.querySelector("#draft-nav-count");
        if (!badge) return;
        badge.hidden = count === 0;
        badge.textContent = count;
        badge.classList.toggle("nav-count-danger", overdue);
        badge.title = `${count} ${count === 1 ? "rascunho" : "rascunhos"} aguardando lançamento no TOTVS`;
    }

    window.JamDraftNav = { DRAFT_DANGER_DAYS, daysSince, summarize, render };

    if (document.querySelector("#request-table-body")) return; // app.js renders it
    fetch("/api/purchase-requests")
        .then(response => response.ok ? response.json() : [])
        .then(requests => render(summarize(requests.filter(request => request.status === "DRAFT"))))
        .catch(() => { /* the counter is optional */ });
})();
