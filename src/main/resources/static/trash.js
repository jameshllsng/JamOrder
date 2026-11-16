const apiUrl = "/api/purchase-requests";
const statusLabels = { DRAFT: "Rascunho", OPEN: "Aguardando entrega", PARTIALLY_RECEIVED: "Recebida parcialmente", COMPLETED: "Concluída", CANCELLED: "Cancelada" };
const feedback = document.querySelector("#operation-feedback");
let csrfToken;
let feedbackTimeout;

document.querySelector("#refresh-trash").addEventListener("click", loadTrash);
document.querySelector("#logout").addEventListener("click", logout);

async function start() {
    const response = await fetch("/api/csrf");
    if (!response.ok) throw new Error("Não foi possível iniciar a sessão.");
    csrfToken = await response.json();
    await loadTrash();
}

async function loadTrash() {
    const response = await fetch(`${apiUrl}/trash`);
    if (response.status === 401) { window.location.assign("/login.html"); return; }
    if (!response.ok) { show(response.status === 403 ? "Somente administradores podem ver a lixeira." : "Não foi possível carregar a lixeira.", "danger"); return; }
    const deleted = await response.json();
    document.querySelector("#trash-count").textContent = `${deleted.length} requisição(ões) na lixeira`;
    document.querySelector("#empty-trash").hidden = deleted.length !== 0;
    const body = document.querySelector("#trash-table-body");
    body.replaceChildren();
    deleted.forEach(request => body.append(createRow(request)));
}

function createRow(request) {
    const row = document.createElement("tr");
    appendCell(row, request.requestNumber ?? "Rascunho");
    appendCell(row, request.departmentName);
    appendCell(row, request.requesterName);
    appendCell(row, request.purchaseReason);
    appendCell(row, statusLabels[request.status] ?? request.status);
    appendCell(row, request.itemCount);
    appendCell(row, formatCurrency(request.totalValue));
    appendCell(row, new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(request.deletedAt)));
    appendCell(row, request.deletedBy);
    const actions = document.createElement("td");
    actions.className = "table-actions";
    const restore = document.createElement("button");
    restore.className = "table-action table-action-edit";
    restore.type = "button";
    restore.textContent = "Restaurar";
    restore.addEventListener("click", () => restoreRequest(request, restore));
    actions.append(restore);
    row.append(actions);
    return row;
}

async function restoreRequest(request, button) {
    button.disabled = true;
    const response = await fetch(`${apiUrl}/${request.id}/restore`, { method: "POST", headers: { [csrfToken.headerName]: csrfToken.token } });
    if (!response.ok) { button.disabled = false; show(await errorMessage(response), "danger"); return; }
    await loadTrash();
    show(`Requisição ${request.requestNumber ?? "(rascunho)"} restaurada.`, "success");
}

function appendCell(row, value) { const cell = document.createElement("td"); cell.textContent = value ?? "Não informado"; row.append(cell); }
function formatCurrency(value) { if (value === null || value === undefined) return "—"; return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value); }
async function errorMessage(response) { const body = await response.json().catch(() => null); return body?.detail ?? "Não foi possível concluir a operação."; }
function show(message, variant) { clearTimeout(feedbackTimeout); feedback.className = `operation-feedback operation-feedback-${variant}`; feedback.textContent = message; feedback.hidden = false; feedbackTimeout = window.setTimeout(() => feedback.hidden = true, 3200); }
async function logout() { await fetch("/logout", { method: "POST", headers: { [csrfToken.headerName]: csrfToken.token } }); window.location.assign("/login.html"); }

start().catch(() => window.location.assign("/login.html"));
