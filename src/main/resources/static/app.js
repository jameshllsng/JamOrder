const apiUrl = "/api/purchase-requests";
const priorityLabels = { LOW: "Baixa", MEDIUM: "Média", HIGH: "Alta", URGENT: "Urgente" };
const statusLabels = { DRAFT: "Rascunho", OPEN: "Aguardando entrega", PARTIALLY_RECEIVED: "Recebida parcialmente", COMPLETED: "Concluída", CANCELLED: "Cancelada" };
// Each summary card is also a shortcut to the status filter with the same key.
const summaryDefinitions = [["DRAFT", "Faltam lançar no TOTVS"], ["PENDING_RECEIPT", "Aguardando entrega"], ["PARTIALLY_RECEIVED", "Parcialmente recebidas"], ["COMPLETED", "Concluídas"]];
const statusFilters = { ALL: () => true, DRAFT: status => status === "DRAFT", PENDING_RECEIPT: status => ["OPEN", "PARTIALLY_RECEIVED"].includes(status), PARTIALLY_RECEIVED: status => status === "PARTIALLY_RECEIVED", COMPLETED: status => status === "COMPLETED", CANCELLED: status => status === "CANCELLED" };
const editableStatuses = ["DRAFT", "OPEN"];
const receivableStatuses = ["OPEN", "PARTIALLY_RECEIVED"];
const AGE_WARNING_DAYS = 30;
const AGE_DANGER_DAYS = 90;
const priorityRank = { LOW: 0, MEDIUM: 1, HIGH: 2, URGENT: 3 };
const statusRank = { DRAFT: 0, OPEN: 1, PARTIALLY_RECEIVED: 2, COMPLETED: 3, CANCELLED: 4 };
const sortValues = {
    number: request => request.requestNumber ?? "",
    department: request => request.departmentName ?? "",
    requester: request => request.requesterName ?? "",
    supplier: request => request.supplierNames ?? "",
    priority: request => priorityRank[request.priority],
    status: request => statusRank[request.status],
    totvsDate: request => request.totvsRequestedOn ?? "",
    openDays: request => openDays(request) ?? -1,
    pending: request => request.pendingItemCount,
    value: request => request.totalValue ?? -1
};
// Columns where the most relevant values (largest, latest, most urgent) should come first.
const descendingFirst = new Set(["priority", "totvsDate", "openDays", "pending", "value"]);

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
const statusFilter = document.querySelector("#status-filter");
const departmentFilter = document.querySelector("#department-filter");
const searchInput = document.querySelector("#request-search");
const rowMenu = document.querySelector("#row-menu");
let sortState = { key: null, direction: "ascending" };
let csrfToken;
let loadedPurchaseRequests = [];
let editingRequestId = null;
let currentUserRole;
let currentUserDisplayName;
let currentUserDepartmentId = null;
let departments = [];
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
document.querySelector("#confirmation-cancel").addEventListener("click", () => resolveConfirmation(false));
document.querySelector("#close-request-details").addEventListener("click", () => requestDetailsDialog.close());
confirmationAction.addEventListener("click", () => resolveConfirmation(true));
confirmationDialog.addEventListener("cancel", event => { event.preventDefault(); resolveConfirmation(false); });
createForm.addEventListener("submit", createPurchaseRequest);
createForm.elements.requestNumber.addEventListener("input", syncTotvsDateRequirement);
statusFilter.addEventListener("change", renderPurchaseRequests);
departmentFilter.addEventListener("change", renderPurchaseRequests);
searchInput.addEventListener("input", renderPurchaseRequests);
setUpSortableHeaders();

function openCreateDialog() { editingRequestId = null; formMessage.hidden = true; document.querySelector("#request-form-title").textContent = "Nova requisição"; setRequesterName(); renderDepartmentOptions(); syncTotvsDateRequirement(); dialog.showModal(); }
async function loadDepartments() { const response = await fetch("/api/departments"); if (!response.ok) throw new Error("Não foi possível carregar os setores."); departments = await response.json(); renderDepartmentOptions(); renderDepartmentFilter(); }
// Only users who see every department get the department filter; inactive departments stay listed for old requisitions.
function renderDepartmentFilter() {
    departmentFilter.replaceChildren(new Option("Todos os setores", ""), ...departments.map(department => new Option(department.name, department.id)));
    departmentFilter.hidden = currentUserDepartmentId !== null || departments.length < 2;
}
// Users bound to a department always register in it; others choose among active departments.
// An inactive department only appears when it is the one already stored on the edited requisition.
function renderDepartmentOptions(selected = null) {
    const select = createForm.elements.departmentId;
    const lockedDepartmentId = currentUserDepartmentId;
    const visible = departments.filter(department => department.active || department.id === selected || department.id === lockedDepartmentId);
    select.replaceChildren(...visible.map(department => new Option(department.active ? department.name : `${department.name} (inativo)`, department.id)));
    if (lockedDepartmentId !== null) select.value = String(lockedDepartmentId);
    else if (selected !== null) select.value = String(selected);
    select.disabled = lockedDepartmentId !== null;
}
function syncTotvsDateRequirement() { const hasNumber = createForm.elements.requestNumber.value.trim() !== ""; const dateInput = createForm.elements.totvsRequestedOn; dateInput.required = hasNumber; if (hasNumber && !dateInput.value) dateInput.value = today(); if (!hasNumber) dateInput.value = ""; }
function closeCreateDialog() { editingRequestId = null; dialog.close(); createForm.reset(); itemForms.replaceChildren(); addItemForm(); setTodayAsRequestedDate(); setRequesterName(); syncTotvsDateRequirement(); }
function addItemForm(insertAtTop = false) { const itemForm = itemFormTemplate.content.cloneNode(true); itemForm.querySelector(".remove-item").addEventListener("click", event => { event.currentTarget.closest(".item-form").remove(); updateRemoveButtons(); }); if (insertAtTop) itemForms.prepend(itemForm); else itemForms.append(itemForm); updateRemoveButtons(); }
function updateRemoveButtons() { const removeButtons = itemForms.querySelectorAll(".remove-item"); removeButtons.forEach(button => button.hidden = removeButtons.length === 1); }

async function loadCsrfToken() { const response = await fetch("/api/csrf"); if (!response.ok) throw new Error("Não foi possível iniciar a sessão."); csrfToken = await response.json(); }
async function loadAuthenticatedUser() { const response = await fetch("/api/auth/me"); if (response.status === 401) { window.location.assign("/login.html"); return false; } if (!response.ok) throw new Error("Não foi possível identificar o usuário autenticado."); const user = await response.json(); currentUserRole = user.role; currentUserDisplayName = user.displayName ?? user.username; currentUserDepartmentId = user.departmentId ?? null; document.querySelector("#current-user-name").textContent = currentUserDisplayName; document.querySelector("#current-user-department").textContent = user.departmentName ?? "Todos os setores"; document.querySelector("#current-user").title = `Login: ${user.username}`; document.querySelector("#users-navigation").hidden = user.role !== "ADMIN"; document.querySelector("#trash-navigation").hidden = user.role !== "ADMIN"; setRequesterName(); return true; }

async function loadPurchaseRequests() { try { const response = await fetch(apiUrl); if (!response.ok) throw new Error(await readErrorMessage(response)); loadedPurchaseRequests = await response.json(); renderPurchaseRequests(); } catch (error) { document.querySelector("#request-count").textContent = error.message; } }

/** Department filter scopes the cards; status filter, search and sorting only affect the table. */
function renderPurchaseRequests() {
    const inDepartment = loadedPurchaseRequests.filter(request => !departmentFilter.value || String(request.departmentId) === departmentFilter.value);
    renderSummaryCards(inDepartment);
    const search = normalizeText(searchInput.value.trim());
    const visible = inDepartment
        .filter(request => statusFilters[statusFilter.value](request.status))
        .filter(request => !search || searchableText(request).includes(search));
    renderPurchaseRequestTable(sortRequests(visible), inDepartment.length);
}

function renderSummaryCards(purchaseRequests) {
    const summaryCards = document.querySelector("#summary-cards");
    summaryCards.replaceChildren(...summaryDefinitions.map(([filter, label]) => {
        const card = document.createElement("button");
        card.type = "button";
        card.className = `summary-card${statusFilter.value === filter ? " summary-card-active" : ""}`;
        card.setAttribute("aria-pressed", String(statusFilter.value === filter));
        card.title = `Filtrar: ${label}`;
        const title = document.createElement("p");
        title.textContent = label;
        const value = document.createElement("strong");
        value.textContent = purchaseRequests.filter(request => statusFilters[filter](request.status)).length;
        card.append(title, value);
        card.addEventListener("click", () => { statusFilter.value = statusFilter.value === filter ? "ALL" : filter; renderPurchaseRequests(); });
        return card;
    }));
}

function renderPurchaseRequestTable(purchaseRequests, totalCount) {
    const tableBody = document.querySelector("#request-table-body");
    document.querySelector("#request-count").textContent = `${purchaseRequests.length} de ${totalCount} requisição(ões)`;
    document.querySelector("#empty-state-title").textContent = totalCount === 0 ? "Nenhuma requisição cadastrada." : "Nenhuma requisição neste filtro.";
    document.querySelector("#empty-state").hidden = purchaseRequests.length !== 0;
    tableBody.replaceChildren(...purchaseRequests.map(createRequestRow));
}

function createRequestRow(request) {
    const row = document.createElement("tr");
    row.className = "clickable-row";
    // The whole row opens the details; buttons inside it keep their own action.
    row.addEventListener("click", event => { if (!event.target.closest("button")) showRequestDetails(request.id); });
    if (request.requestNumber) appendCell(row, request.requestNumber); else appendCell(row, "Rascunho", "draft-number");
    appendCell(row, request.departmentName);
    appendCell(row, request.requesterName);
    if (request.supplierNames) appendCell(row, request.supplierNames); else { appendCell(row, "—", "value-missing"); row.lastElementChild.title = "Fornecedor não informado"; }
    appendBadgeCell(row, priorityLabels[request.priority], `priority-${request.priority.toLowerCase()}`);
    appendBadgeCell(row, statusLabels[request.status], `status-${request.status.toLowerCase().replaceAll("_", "-")}`);
    if (request.approvedAt) appendApprovalMark(row.lastElementChild, request);
    appendCell(row, request.totvsRequestedOn ? formatDate(request.totvsRequestedOn) : "—");
    appendOpenDaysCell(row, request);
    appendCell(row, `${request.pendingItemCount} de ${request.itemCount}`);
    if (request.totalValue === null || request.totalValue === undefined) { appendCell(row, "—", "value-missing"); row.lastElementChild.title = "Valor não informado"; }
    else appendCell(row, formatCurrency(request.totalValue));
    appendActionCell(row, request);
    return row;
}

function appendCell(row, value, className) { const cell = document.createElement("td"); cell.textContent = value ?? "Não informado"; if (className) cell.className = className; row.append(cell); }
function appendBadgeCell(row, value, className) { const cell = document.createElement("td"); const badge = document.createElement("span"); badge.className = `badge ${className}`; badge.textContent = value; cell.append(badge); row.append(cell); }
function appendApprovalMark(cell, request) { const mark = document.createElement("span"); mark.className = "approval-mark"; mark.textContent = "✓"; const label = `Aprovada por ${request.approvedBy} em ${formatDateTime(request.approvedAt)}`; mark.title = label; mark.setAttribute("aria-label", label); mark.setAttribute("role", "img"); cell.append(mark); }
function appendOpenDaysCell(row, request) {
    const days = openDays(request);
    if (days === null) { appendCell(row, "—"); return; }
    appendCell(row, formatDays(days), days > AGE_DANGER_DAYS ? "age-danger" : days > AGE_WARNING_DAYS ? "age-warning" : undefined);
}

// ---- Row actions: "Detalhes" stays visible, everything else lives in the "⋯" menu ----

function appendActionCell(row, request) {
    const cell = document.createElement("td");
    cell.className = "table-actions";
    cell.append(actionButton("Detalhes", "edit", () => showRequestDetails(request.id)));
    const menuActions = rowMenuActions(request);
    if (menuActions.length > 0) {
        const more = actionButton("⋯", "more", event => openRowMenu(event.currentTarget, menuActions));
        more.title = "Mais ações";
        more.setAttribute("aria-label", "Mais ações");
        more.setAttribute("aria-haspopup", "menu");
        cell.append(more);
    }
    row.append(cell);
}

function rowMenuActions(request) {
    const actions = [];
    // Approval is informational only: it never changes the requisition status.
    if (["ADMIN", "MANAGER"].includes(currentUserRole) && request.status !== "CANCELLED") {
        actions.push(request.approvedAt
            ? { label: "Remover aprovação", run: () => changeApproval(request.id, "DELETE", "Aprovação removida.") }
            : { label: "✓ Aprovar", run: () => changeApproval(request.id, "POST", "Requisição aprovada.") });
    }
    if (currentUserRole === "ADMIN" && editableStatuses.includes(request.status)) {
        actions.push({ label: request.status === "DRAFT" ? "Lançar no TOTVS" : "Editar", run: () => editPurchaseRequest(request.id) });
        actions.push({ label: "Cancelar requisição", run: () => cancelPurchaseRequest(request.id) });
    }
    if (currentUserRole === "ADMIN") {
        actions.push({ label: "Mover para a lixeira", run: () => deletePurchaseRequest(request.id), danger: true, separated: actions.length > 0 });
    }
    return actions;
}

function openRowMenu(trigger, actions) {
    rowMenu.replaceChildren();
    actions.forEach(action => {
        if (action.separated) rowMenu.append(document.createElement("hr"));
        const item = document.createElement("button");
        item.type = "button";
        item.setAttribute("role", "menuitem");
        item.textContent = action.label;
        if (action.danger) item.className = "menu-danger";
        item.addEventListener("click", () => { rowMenu.hidePopover(); action.run(); });
        rowMenu.append(item);
    });
    rowMenu.showPopover();
    const rect = trigger.getBoundingClientRect();
    const left = Math.max(8, Math.min(rect.right - rowMenu.offsetWidth, window.innerWidth - rowMenu.offsetWidth - 8));
    const below = rect.bottom + 4;
    const top = below + rowMenu.offsetHeight > window.innerHeight - 8 ? rect.top - rowMenu.offsetHeight - 4 : below;
    rowMenu.style.left = `${left}px`;
    rowMenu.style.top = `${Math.max(8, top)}px`;
    rowMenu.querySelector("button")?.focus();
}

async function changeApproval(id, method, message) { await performRequest(`${apiUrl}/${id}/approval`, method, message, "success"); }

// ---- Search and sorting ----

function normalizeText(value) { return value.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase(); }
function searchableText(request) {
    return normalizeText([request.requestNumber, request.purchaseReason, request.requesterName, request.departmentName, request.usageLocation, request.itemDescriptions, request.supplierNames].filter(Boolean).join(" "));
}

function setUpSortableHeaders() {
    document.querySelectorAll("th[data-sort]").forEach(header => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "sort-button";
        button.textContent = header.textContent;
        button.addEventListener("click", () => toggleSort(header.dataset.sort));
        header.replaceChildren(button);
        header.setAttribute("aria-sort", "none");
    });
}

function toggleSort(key) {
    if (sortState.key === key) sortState.direction = sortState.direction === "ascending" ? "descending" : "ascending";
    else sortState = { key, direction: descendingFirst.has(key) ? "descending" : "ascending" };
    document.querySelectorAll("th[data-sort]").forEach(header => header.setAttribute("aria-sort", header.dataset.sort === sortState.key ? sortState.direction : "none"));
    renderPurchaseRequests();
}

/** Without a chosen column, keeps the server order (newest first). */
function sortRequests(requests) {
    if (!sortState.key) return requests;
    const valueOf = sortValues[sortState.key];
    const direction = sortState.direction === "ascending" ? 1 : -1;
    return [...requests].sort((first, second) => {
        const a = valueOf(first); const b = valueOf(second);
        const comparison = typeof a === "number" && typeof b === "number" ? a - b : String(a).localeCompare(String(b), "pt-BR", { numeric: true, sensitivity: "base" });
        return comparison * direction;
    });
}

/** Days a requisition has been open, or null when it is already finished. */
function openDays(request) {
    if (!receivableStatuses.includes(request.status) && request.status !== "DRAFT") return null;
    return daysSince(request.totvsRequestedOn ?? request.requestedOn);
}
function actionButton(label, variant, action) { const button = document.createElement("button"); button.className = `table-action table-action-${variant}`; button.type = "button"; button.textContent = label; button.title = label; button.setAttribute("aria-label", label); button.addEventListener("click", action); return button; }

async function showRequestDetails(id) {
    try {
        const [requestResponse, historyResponse] = await Promise.all([fetch(`${apiUrl}/${id}`), fetch(`${apiUrl}/${id}/history`)]);
        if (!requestResponse.ok) throw new Error(await readErrorMessage(requestResponse));
        if (!historyResponse.ok) throw new Error(await readErrorMessage(historyResponse));
        const request = await requestResponse.json(); const history = await historyResponse.json();
        document.querySelector("#request-details-title").textContent = request.requestNumber ? `Requisição ${request.requestNumber}` : "Rascunho (falta lançar no TOTVS)";
        document.querySelector("#request-details-subtitle").textContent = [statusLabels[request.status], request.departmentName, request.usageLocation ? `Local: ${request.usageLocation}` : null, request.costCenter ? `CC ${request.costCenter}` : null, request.approvedAt ? `Aprovada por ${request.approvedBy} em ${formatDateTime(request.approvedAt)}` : null].filter(Boolean).join(" · ");
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
    if (canReceive && ["ADMIN", "BUYER", "REQUESTER"].includes(currentUserRole) && item.status !== "RECEIVED") {
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
    const request = { requestNumber, totvsRequestedOn: blankToNull(formData.get("totvsRequestedOn")), departmentId: Number(createForm.elements.departmentId.value) || null, usageLocation: blankToNull(formData.get("usageLocation")), requestedOn: formData.get("requestedOn"), purchaseReason: formData.get("purchaseReason"), priority: formData.get("priority"), costCenter: blankToNull(formData.get("costCenter")), notes: blankToNull(formData.get("notes")), items };
    const message = !requestNumber ? "Rascunho salvo. Lance no TOTVS e informe o número depois." : editingRequestId ? "Requisição atualizada." : "Requisição cadastrada.";
    try { const response = await fetch(editingRequestId ? `${apiUrl}/${editingRequestId}` : apiUrl, { method: editingRequestId ? "PUT" : "POST", headers: { "Content-Type": "application/json", [csrfToken.headerName]: csrfToken.token }, body: JSON.stringify(request) }); if (!response.ok) throw new Error(await readErrorMessage(response)); closeCreateDialog(); await loadPurchaseRequests(); showOperationFeedback(message, "success"); } catch (error) { formMessage.textContent = error.message; formMessage.hidden = false; showOperationFeedback(error.message, "danger"); }
}

async function editPurchaseRequest(id) { const response = await fetch(`${apiUrl}/${id}`); if (!response.ok) { window.alert(await readErrorMessage(response)); return; } const request = await response.json(); editingRequestId = id; formMessage.hidden = true; document.querySelector("#request-form-title").textContent = request.status === "DRAFT" ? "Lançar rascunho no TOTVS" : "Editar requisição"; createForm.elements.requestNumber.value = request.requestNumber ?? ""; createForm.elements.totvsRequestedOn.value = request.totvsRequestedOn ?? ""; syncTotvsDateRequirement(); createForm.elements.costCenter.value = request.costCenter ?? ""; renderDepartmentOptions(request.departmentId); createForm.elements.usageLocation.value = request.usageLocation ?? ""; createForm.elements.requesterName.value = request.requesterName; createForm.elements.requestedOn.value = request.requestedOn; createForm.elements.purchaseReason.value = request.purchaseReason; createForm.elements.priority.value = request.priority; createForm.elements.notes.value = request.notes ?? ""; itemForms.replaceChildren(); request.items.forEach(item => { addItemForm(); const form = itemForms.lastElementChild; form.querySelector(".item-description").value = item.description; form.querySelector(".item-brand-model").value = item.brandModel ?? ""; form.querySelector(".item-quantity").value = item.quantity; form.querySelector(".item-supplier-name").value = item.supplierName ?? ""; form.querySelector(".item-total-value").value = item.totalValue ?? ""; form.querySelector(".item-notes").value = item.notes ?? ""; }); dialog.showModal(); }
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
function formatDateTime(value) { return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)); }
function formatDate(value) { return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)); }
function daysSince(value) { const since = new Date(`${value}T00:00:00Z`); const now = new Date(); const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()); return Math.max(0, Math.floor((today - since.getTime()) / 86_400_000)); }
function formatDays(days) { if (days === 0) return "Hoje"; return `${days} ${days === 1 ? "dia" : "dias"}`; }
function setTodayAsRequestedDate() { createForm.elements.requestedOn.value = today(); }
function setRequesterName() { if (currentUserDisplayName) createForm.elements.requesterName.value = currentUserDisplayName; }

async function start() { setTodayAsRequestedDate(); addItemForm(); await loadCsrfToken(); if (await loadAuthenticatedUser()) { await loadDepartments(); await loadPurchaseRequests(); } }
start().catch(error => { document.querySelector("#request-count").textContent = error.message; });
