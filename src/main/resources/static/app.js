const apiUrl = "/api/purchase-requests";
const priorityLabels = { LOW: "Baixa", MEDIUM: "Média", HIGH: "Alta", URGENT: "Urgente" };
const statusLabels = { DRAFT: "Rascunho", OPEN: "Aguardando entrega", PARTIALLY_RECEIVED: "Recebida parcialmente", COMPLETED: "Concluída", CANCELLED: "Cancelada" };
const summaryDefinitions = [["DRAFT", "Faltam lançar no TOTVS"], ["OPEN", "Aguardando entrega"], ["PARTIALLY_RECEIVED", "Parcialmente recebidas"], ["COMPLETED", "Concluídas"]];
const statusFilters = { ALL: () => true, DRAFT: status => status === "DRAFT", PENDING_RECEIPT: status => ["OPEN", "PARTIALLY_RECEIVED"].includes(status), COMPLETED: status => status === "COMPLETED", CANCELLED: status => status === "CANCELLED" };
const editableStatuses = ["DRAFT", "OPEN"];
const receivableStatuses = ["OPEN", "PARTIALLY_RECEIVED"];

const dialog = document.querySelector("#create-request-dialog");
const createForm = document.querySelector("#create-request-form");
const itemForms = document.querySelector("#item-forms");
const itemFormTemplate = document.querySelector("#item-form-template");
const formMessage = document.querySelector("#form-message");
const operationFeedback = document.querySelector("#operation-feedback");
const confirmationDialog = document.querySelector("#confirmation-dialog");
const confirmationTitle = document.querySelector("#confirmation-title");
const confirmationMessage = document.querySelector("#confirmation-message");
const confirmationAction = document.querySelector("#confirmation-action");
const requestDetailsDialog = document.querySelector("#request-details-dialog");
const requestDetailsItems = document.querySelector("#request-details-items");
const requestHistory = document.querySelector("#request-history");
const themeToggle = document.querySelector("#theme-toggle");
const statusFilter = document.querySelector("#status-filter");
let csrfToken;
let loadedPurchaseRequests = [];
let editingRequestId = null;
let currentUserRole;
let currentUserDisplayName;
let feedbackTimeout;
let feedbackAnimation;
let confirmationResolver;

document.querySelector("#open-create-form").addEventListener("click", openCreateDialog);
document.querySelector("#open-create-empty").addEventListener("click", openCreateDialog);
document.querySelector("#close-create-form").addEventListener("click", closeCreateDialog);
document.querySelector("#cancel-create").addEventListener("click", closeCreateDialog);
document.querySelector("#add-item").addEventListener("click", () => addItemForm(true));
document.querySelector("#refresh-list").addEventListener("click", loadPurchaseRequests);
document.querySelector("#logout").addEventListener("click", logout);
themeToggle.addEventListener("click", toggleTheme);
document.querySelector("#confirmation-cancel").addEventListener("click", () => resolveConfirmation(false));
document.querySelector("#close-request-details").addEventListener("click", () => requestDetailsDialog.close());
confirmationAction.addEventListener("click", () => resolveConfirmation(true));
confirmationDialog.addEventListener("cancel", event => { event.preventDefault(); resolveConfirmation(false); });
createForm.addEventListener("submit", createPurchaseRequest);
createForm.elements.requestNumber.addEventListener("input", syncTotvsDateRequirement);
statusFilter.addEventListener("change", () => renderPurchaseRequestTable(loadedPurchaseRequests));

function openCreateDialog() { editingRequestId = null; formMessage.hidden = true; document.querySelector("#request-form-title").textContent = "Nova requisição"; setRequesterName(); syncTotvsDateRequirement(); dialog.showModal(); }
function syncTotvsDateRequirement() { const hasNumber = createForm.elements.requestNumber.value.trim() !== ""; const dateInput = createForm.elements.totvsRequestedOn; dateInput.required = hasNumber; if (hasNumber && !dateInput.value) dateInput.value = today(); if (!hasNumber) dateInput.value = ""; }
function closeCreateDialog() { editingRequestId = null; dialog.close(); createForm.reset(); itemForms.replaceChildren(); addItemForm(); setTodayAsRequestedDate(); setRequesterName(); syncTotvsDateRequirement(); }
function addItemForm(insertAtTop = false) { const itemForm = itemFormTemplate.content.cloneNode(true); itemForm.querySelector(".remove-item").addEventListener("click", event => { event.currentTarget.closest(".item-form").remove(); updateRemoveButtons(); }); if (insertAtTop) itemForms.prepend(itemForm); else itemForms.append(itemForm); updateRemoveButtons(); }
function updateRemoveButtons() { const removeButtons = itemForms.querySelectorAll(".remove-item"); removeButtons.forEach(button => button.hidden = removeButtons.length === 1); }

async function loadCsrfToken() { const response = await fetch("/api/csrf"); if (!response.ok) throw new Error("Não foi possível iniciar a sessão."); csrfToken = await response.json(); }
async function loadAuthenticatedUser() { const response = await fetch("/api/auth/me"); if (response.status === 401) { window.location.assign("/login.html"); return false; } if (!response.ok) throw new Error("Não foi possível identificar o usuário autenticado."); const user = await response.json(); currentUserRole = user.role; currentUserDisplayName = user.displayName ?? user.username; document.querySelector("#current-user").textContent = user.username; document.querySelector("#users-navigation").hidden = user.role !== "ADMIN"; document.querySelector("#trash-navigation").hidden = user.role !== "ADMIN"; setRequesterName(); return true; }

async function loadPurchaseRequests() { try { const response = await fetch(apiUrl); if (!response.ok) throw new Error(await readErrorMessage(response)); loadedPurchaseRequests = await response.json(); renderSummaryCards(loadedPurchaseRequests); renderPurchaseRequestTable(loadedPurchaseRequests); } catch (error) { document.querySelector("#request-count").textContent = error.message; } }
function renderSummaryCards(purchaseRequests) { const summaryCards = document.querySelector("#summary-cards"); summaryCards.replaceChildren(); summaryDefinitions.forEach(([status, label]) => { const card = document.createElement("article"); card.className = "summary-card"; const title = document.createElement("p"); title.textContent = label; const value = document.createElement("strong"); value.textContent = purchaseRequests.filter(purchaseRequest => purchaseRequest.status === status).length; card.append(title, value); summaryCards.append(card); }); }
function renderPurchaseRequestTable(allPurchaseRequests) {
    const tableBody = document.querySelector("#request-table-body");
    const emptyState = document.querySelector("#empty-state");
    const purchaseRequests = allPurchaseRequests.filter(purchaseRequest => statusFilters[statusFilter.value](purchaseRequest.status));
    tableBody.replaceChildren();
    document.querySelector("#request-count").textContent = `${purchaseRequests.length} de ${allPurchaseRequests.length} requisição(ões)`;
    document.querySelector("#empty-state-title").textContent = allPurchaseRequests.length === 0 ? "Nenhuma requisição cadastrada." : "Nenhuma requisição neste filtro.";
    emptyState.hidden = purchaseRequests.length !== 0;
    purchaseRequests.forEach(purchaseRequest => {
        const row = document.createElement("tr");
        if (purchaseRequest.requestNumber) appendCell(row, purchaseRequest.requestNumber); else appendCell(row, "Rascunho", "draft-number");
        appendCell(row, purchaseRequest.department);
        appendCell(row, purchaseRequest.requesterName);
        appendCell(row, purchaseRequest.purchaseReason);
        appendBadgeCell(row, priorityLabels[purchaseRequest.priority], `priority-${purchaseRequest.priority.toLowerCase()}`);
        appendBadgeCell(row, statusLabels[purchaseRequest.status], `status-${purchaseRequest.status.toLowerCase().replaceAll("_", "-")}`);
        appendCell(row, purchaseRequest.totvsRequestedOn ? formatDate(purchaseRequest.totvsRequestedOn) : "—");
        appendCell(row, receivableStatuses.includes(purchaseRequest.status) || purchaseRequest.status === "DRAFT" ? formatOpenDuration(purchaseRequest.totvsRequestedOn ?? purchaseRequest.requestedOn) : "—");
        appendCell(row, `${purchaseRequest.pendingItemCount} de ${purchaseRequest.itemCount}`);
        appendCell(row, formatCurrency(purchaseRequest.totalValue));
        appendActionCell(row, purchaseRequest);
        tableBody.append(row);
    });
}
function appendCell(row, value, className) { const cell = document.createElement("td"); cell.textContent = value ?? "Não informado"; if (className) cell.className = className; row.append(cell); }
function appendBadgeCell(row, value, className) { const cell = document.createElement("td"); const badge = document.createElement("span"); badge.className = `badge ${className}`; badge.textContent = value; cell.append(badge); row.append(cell); }
function appendActionCell(row, request) { const cell = document.createElement("td"); cell.className = "table-actions"; cell.append(actionButton("Detalhes", "edit", () => showRequestDetails(request.id))); if (currentUserRole === "ADMIN" && editableStatuses.includes(request.status)) cell.append(actionButton(request.status === "DRAFT" ? "Lançar" : "Editar", "edit", () => editPurchaseRequest(request.id)), actionButton("Cancelar", "cancel", () => cancelPurchaseRequest(request.id))); if (currentUserRole === "ADMIN") cell.append(actionButton("Excluir", "delete", () => deletePurchaseRequest(request.id))); row.append(cell); }
function actionButton(label, variant, action) { const button = document.createElement("button"); button.className = `table-action table-action-${variant}`; button.type = "button"; button.textContent = label; button.title = label; button.setAttribute("aria-label", label); button.addEventListener("click", action); return button; }

async function showRequestDetails(id) {
    try {
        const [requestResponse, historyResponse] = await Promise.all([fetch(`${apiUrl}/${id}`), fetch(`${apiUrl}/${id}/history`)]);
        if (!requestResponse.ok) throw new Error(await readErrorMessage(requestResponse));
        if (!historyResponse.ok) throw new Error(await readErrorMessage(historyResponse));
        const request = await requestResponse.json(); const history = await historyResponse.json();
        document.querySelector("#request-details-title").textContent = request.requestNumber ? `Requisição ${request.requestNumber}` : "Rascunho (falta lançar no TOTVS)";
        document.querySelector("#request-details-subtitle").textContent = [statusLabels[request.status], request.department, request.costCenter ? `CC ${request.costCenter}` : null].filter(Boolean).join(" · ");
        const canReceive = receivableStatuses.includes(request.status);
        requestDetailsItems.replaceChildren(); request.items.forEach(item => requestDetailsItems.append(createDetailItem(request.id, item, canReceive)));
        requestHistory.replaceChildren(); history.forEach(entry => { const item = document.createElement("li"); const title = document.createElement("strong"); title.textContent = entry.details; const detail = document.createElement("span"); detail.textContent = `${entry.performedBy} · ${new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(entry.createdAt))}`; item.append(title, detail); requestHistory.append(item); });
        if (history.length === 0) { const item = document.createElement("li"); item.textContent = "Nenhuma alteração registrada."; requestHistory.append(item); }
        if (!requestDetailsDialog.open) requestDetailsDialog.showModal();
    } catch (error) { showOperationFeedback(error.message, "danger"); }
}
function createDetailItem(requestId, item, canReceive) {
    const container = document.createElement("article"); container.className = "detail-item";
    const header = document.createElement("div"); header.className = "detail-item-header";
    const title = document.createElement("h4"); title.textContent = [item.description, item.brandModel].filter(Boolean).join(" · ");
    const badge = document.createElement("span"); badge.className = `badge status-${item.status.toLowerCase().replaceAll("_", "-")}`; badge.textContent = item.status === "PARTIALLY_RECEIVED" ? "Recebido parcialmente" : item.status === "RECEIVED" ? "Recebido" : "Pendente";
    header.append(title, badge);
    const summary = document.createElement("p");
    summary.textContent = [`Recebido: ${item.receivedQuantity} de ${item.quantity}`, item.supplierName ? `Fornecedor: ${item.supplierName}` : null, `Valor: ${formatCurrency(item.totalValue)}`, item.receivedOn ? `Último recebimento: ${formatDate(item.receivedOn)}` : null, item.checkedBy ? `Conferido por: ${item.checkedBy}` : null].filter(Boolean).join(" · ");
    container.append(header, summary);
    if (canReceive && ["ADMIN", "BUYER"].includes(currentUserRole) && item.status !== "RECEIVED") {
        const remaining = item.quantity - item.receivedQuantity;
        const actions = document.createElement("div"); actions.className = "receipt-actions";
        const quickButton = document.createElement("button"); quickButton.className = "button button-primary"; quickButton.type = "button"; quickButton.textContent = remaining === item.quantity ? "Recebido" : `Recebido (restam ${remaining})`;
        quickButton.addEventListener("click", () => registerReceipt(requestId, item.id, remaining, today()));
        const partial = document.createElement("details"); const partialSummary = document.createElement("summary"); partialSummary.textContent = "Recebimento parcial ou em outra data";
        const form = document.createElement("form"); form.className = "receipt-form";
        const quantityLabel = document.createElement("label"); quantityLabel.textContent = "Quantidade"; const quantity = document.createElement("input"); quantity.type = "number"; quantity.min = "1"; quantity.max = String(remaining); quantity.value = quantity.max; quantity.required = true; quantityLabel.append(quantity);
        const dateLabel = document.createElement("label"); dateLabel.textContent = "Data do recebimento"; const date = document.createElement("input"); date.type = "date"; date.value = today(); date.max = today(); date.required = true; dateLabel.append(date);
        const button = document.createElement("button"); button.className = "button button-quiet"; button.type = "submit"; button.textContent = "Registrar";
        form.append(quantityLabel, dateLabel, button); form.addEventListener("submit", event => { event.preventDefault(); registerReceipt(requestId, item.id, Number(quantity.value), date.value); });
        partial.append(partialSummary, form); actions.append(quickButton, partial); container.append(actions);
    } return container;
}
async function registerReceipt(requestId, itemId, receivedQuantity, receivedOn) { const response = await fetch(`${apiUrl}/${requestId}/items/${itemId}/receipts`, { method: "POST", headers: { "Content-Type": "application/json", [csrfToken.headerName]: csrfToken.token }, body: JSON.stringify({ receivedQuantity, receivedOn }) }); if (!response.ok) { showOperationFeedback(await readErrorMessage(response), "danger"); return; } await loadPurchaseRequests(); await showRequestDetails(requestId); showOperationFeedback("Recebimento registrado.", "success"); }

async function createPurchaseRequest(event) {
    event.preventDefault(); formMessage.hidden = true;
    const formData = new FormData(createForm);
    const items = [...itemForms.querySelectorAll(".item-form")].map(itemForm => ({ description: itemForm.querySelector(".item-description").value, brandModel: blankToNull(itemForm.querySelector(".item-brand-model").value), quantity: Number(itemForm.querySelector(".item-quantity").value), supplierName: blankToNull(itemForm.querySelector(".item-supplier-name").value), totalValue: blankToNull(itemForm.querySelector(".item-total-value").value) === null ? null : Number(itemForm.querySelector(".item-total-value").value), notes: blankToNull(itemForm.querySelector(".item-notes").value) }));
    const requestNumber = blankToNull(formData.get("requestNumber"));
    const request = { requestNumber, totvsRequestedOn: blankToNull(formData.get("totvsRequestedOn")), department: formData.get("department"), requestedOn: formData.get("requestedOn"), purchaseReason: formData.get("purchaseReason"), priority: formData.get("priority"), costCenter: blankToNull(formData.get("costCenter")), notes: blankToNull(formData.get("notes")), items };
    const message = !requestNumber ? "Rascunho salvo. Lance no TOTVS e informe o número depois." : editingRequestId ? "Requisição atualizada." : "Requisição cadastrada.";
    try { const response = await fetch(editingRequestId ? `${apiUrl}/${editingRequestId}` : apiUrl, { method: editingRequestId ? "PUT" : "POST", headers: { "Content-Type": "application/json", [csrfToken.headerName]: csrfToken.token }, body: JSON.stringify(request) }); if (!response.ok) throw new Error(await readErrorMessage(response)); closeCreateDialog(); await loadPurchaseRequests(); showOperationFeedback(message, "success"); } catch (error) { formMessage.textContent = error.message; formMessage.hidden = false; showOperationFeedback(error.message, "danger"); }
}

async function editPurchaseRequest(id) { const response = await fetch(`${apiUrl}/${id}`); if (!response.ok) { window.alert(await readErrorMessage(response)); return; } const request = await response.json(); editingRequestId = id; formMessage.hidden = true; document.querySelector("#request-form-title").textContent = request.status === "DRAFT" ? "Lançar rascunho no TOTVS" : "Editar requisição"; createForm.elements.requestNumber.value = request.requestNumber ?? ""; createForm.elements.totvsRequestedOn.value = request.totvsRequestedOn ?? ""; syncTotvsDateRequirement(); createForm.elements.costCenter.value = request.costCenter ?? ""; createForm.elements.department.value = request.department; createForm.elements.requesterName.value = request.requesterName; createForm.elements.requestedOn.value = request.requestedOn; createForm.elements.purchaseReason.value = request.purchaseReason; createForm.elements.priority.value = request.priority; createForm.elements.notes.value = request.notes ?? ""; itemForms.replaceChildren(); request.items.forEach(item => { addItemForm(); const form = itemForms.lastElementChild; form.querySelector(".item-description").value = item.description; form.querySelector(".item-brand-model").value = item.brandModel ?? ""; form.querySelector(".item-quantity").value = item.quantity; form.querySelector(".item-supplier-name").value = item.supplierName ?? ""; form.querySelector(".item-total-value").value = item.totalValue ?? ""; form.querySelector(".item-notes").value = item.notes ?? ""; }); dialog.showModal(); }
async function cancelPurchaseRequest(id) { if (!(await askConfirmation("Cancelar requisição?", "A requisição deixará de aceitar alterações, mas continuará disponível no histórico.", "Cancelar requisição", "warning"))) return; await performRequest(`${apiUrl}/${id}/cancel`, "POST", "Requisição cancelada.", "warning"); }
async function deletePurchaseRequest(id) { if (!(await askConfirmation("Mover para a lixeira?", "A requisição deixará de aparecer no sistema. Um administrador pode restaurá-la pela Lixeira.", "Mover para a lixeira", "danger"))) return; await performRequest(`${apiUrl}/${id}`, "DELETE", "Requisição movida para a lixeira.", "danger"); }
async function performRequest(url, method, message, variant) { const response = await fetch(url, { method, headers: { [csrfToken.headerName]: csrfToken.token } }); if (!response.ok) { showOperationFeedback(await readErrorMessage(response), "danger"); return; } await loadPurchaseRequests(); showOperationFeedback(message, variant); }
function showOperationFeedback(message, variant) { clearTimeout(feedbackTimeout); feedbackAnimation?.cancel(); operationFeedback.className = `operation-feedback operation-feedback-${variant}`; operationFeedback.textContent = message; operationFeedback.hidden = false; feedbackAnimation = operationFeedback.animate([{ opacity: 0, transform: "translate(-50%, -28px)" }, { opacity: 1, transform: "translate(-50%, 0)" }], { duration: 420, easing: "cubic-bezier(0.22, 1, 0.36, 1)", fill: "forwards" }); feedbackTimeout = window.setTimeout(hideOperationFeedback, 3200); }
function hideOperationFeedback() { feedbackAnimation?.cancel(); feedbackAnimation = operationFeedback.animate([{ opacity: 1, transform: "translate(-50%, 0)" }, { opacity: 0, transform: "translate(-50%, -18px)" }], { duration: 260, easing: "ease-in", fill: "forwards" }); feedbackAnimation.finished.then(() => operationFeedback.hidden = true); }
function askConfirmation(title, message, actionLabel, variant) { confirmationTitle.textContent = title; confirmationMessage.textContent = message; confirmationAction.textContent = actionLabel; confirmationAction.className = `button button-${variant}`; confirmationDialog.showModal(); return new Promise(resolve => confirmationResolver = resolve); }
function resolveConfirmation(confirmed) { confirmationDialog.close(); confirmationResolver?.(confirmed); confirmationResolver = null; }

async function logout() { const response = await fetch("/logout", { method: "POST", headers: { [csrfToken.headerName]: csrfToken.token } }); if (response.status === 204) window.location.assign("/login.html"); else window.alert("Não foi possível encerrar a sessão."); }
async function readErrorMessage(response) { if (response.status === 401) return "Sua sessão expirou. Entre novamente."; if (response.status === 403) return "Você não tem permissão para esta operação."; const error = await response.json().catch(() => null); return error?.errors ? Object.values(error.errors).join(" ") : error?.detail ?? "Não foi possível concluir a operação."; }
function blankToNull(value) { return value?.trim() || null; }
function today() { const now = new Date(); return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10); }
function formatCurrency(value) { if (value === null || value === undefined) return "Não informado"; return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value); }
function formatDate(value) { return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)); }
function formatOpenDuration(value) { const requestedOn = new Date(`${value}T00:00:00Z`); const now = new Date(); const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()); const days = Math.max(0, Math.floor((today - requestedOn.getTime()) / 86_400_000)); if (days === 0) return "Hoje"; return `${days} ${days === 1 ? "dia" : "dias"}`; }
function setTodayAsRequestedDate() { createForm.elements.requestedOn.value = today(); }
function setRequesterName() { if (currentUserDisplayName) createForm.elements.requesterName.value = currentUserDisplayName; }

async function start() { setTodayAsRequestedDate(); addItemForm(); await loadCsrfToken(); if (await loadAuthenticatedUser()) await loadPurchaseRequests(); }
function toggleTheme() { applyTheme(document.body.classList.contains("theme-light") ? "dark" : "light"); }
function applyTheme(theme) { const isLight = theme === "light"; document.body.classList.toggle("theme-light", isLight); themeToggle.textContent = isLight ? "◐" : "☀"; themeToggle.setAttribute("aria-label", isLight ? "Ativar modo escuro" : "Ativar modo claro"); themeToggle.title = themeToggle.getAttribute("aria-label"); localStorage.setItem("jamorder-theme", theme); }
applyTheme(localStorage.getItem("jamorder-theme") ?? "dark");
start().catch(error => { document.querySelector("#request-count").textContent = error.message; });
