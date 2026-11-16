const usersUrl = "/api/users";
const departmentsUrl = "/api/departments";
const roles = ["REQUESTER", "BUYER", "MANAGER", "ADMIN"];
const feedback = document.querySelector("#operation-feedback");
const createUserForm = document.querySelector("#create-user-form");
const createUserMessage = document.querySelector("#create-user-message");
const createUserButton = document.querySelector("#create-user-button");
const createDepartmentForm = document.querySelector("#create-department-form");
let csrfToken;
let departments = [];
let passwordResetUserId;

document.querySelector("#refresh-users").addEventListener("click", loadAll);
createUserForm.addEventListener("submit", createUser);
createDepartmentForm.addEventListener("submit", createDepartment);
document.querySelector("#logout").addEventListener("click", logout);
document.querySelector("#close-password-dialog").addEventListener("click", () => document.querySelector("#password-dialog").close());
document.querySelector("#reset-password-form").addEventListener("submit", submitPasswordReset);

async function start() {
    const response = await fetch("/api/csrf");
    csrfToken = await response.json();
    await loadAll();
}

async function loadAll() {
    await loadDepartments();
    await loadUsers();
}

// ---- Departments ----

async function loadDepartments() {
    const response = await fetch(departmentsUrl);
    if (!response.ok) return show("Não foi possível carregar os setores.", "danger");
    departments = await response.json();
    document.querySelector("#department-count").textContent = `${departments.length} setor(es) cadastrado(s)`;
    const body = document.querySelector("#department-table-body");
    body.replaceChildren(...departments.map(createDepartmentRow));
    const createSelect = createUserForm.elements.departmentId;
    createSelect.replaceChildren(...departmentOptions(null));
}

function createDepartmentRow(department) {
    const row = document.createElement("tr");
    const nameCell = document.createElement("td");
    const name = document.createElement("input");
    name.value = department.name;
    name.maxLength = 120;
    nameCell.append(name);
    const state = document.createElement("td");
    state.textContent = department.active ? "Ativo" : "Inativo";
    const actions = document.createElement("td");
    actions.className = "table-actions";
    actions.append(
        actionButton("Salvar", "edit", () => updateDepartment(department, name.value, department.active)),
        actionButton(department.active ? "Desativar" : "Ativar", "cancel", () => updateDepartment(department, name.value, !department.active)));
    row.append(nameCell, state, actions);
    return row;
}

async function createDepartment(event) {
    event.preventDefault();
    const name = createDepartmentForm.elements.name.value.trim();
    if (!name) return show("Informe o nome do setor.", "danger");
    const response = await send(departmentsUrl, "POST", { name, active: true });
    if (!response.ok) return show(await errorMessage(response), "danger");
    createDepartmentForm.reset();
    await loadAll();
    show("Setor adicionado.", "success");
}

async function updateDepartment(department, name, active) {
    const response = await send(`${departmentsUrl}/${department.id}`, "PUT", { name, active });
    if (!response.ok) return show(await errorMessage(response), "danger");
    await loadAll();
    show("Setor atualizado.", "success");
}

/** "Todos os setores" (no department) plus active departments; an inactive one only if already assigned. */
function departmentOptions(selectedId) {
    const options = [new Option("Todos os setores", "")];
    departments
        .filter(department => department.active || department.id === selectedId)
        .forEach(department => options.push(new Option(department.active ? department.name : `${department.name} (inativo)`, department.id)));
    options.forEach(option => option.selected = option.value === String(selectedId ?? ""));
    return options;
}

// ---- Users ----

async function loadUsers() {
    const response = await fetch(usersUrl);
    if (!response.ok) return show("Não foi possível carregar os usuários.", "danger");
    const users = await response.json();
    document.querySelector("#user-count").textContent = `${users.length} usuário(s) cadastrado(s)`;
    document.querySelector("#user-table-body").replaceChildren(...users.map(createUserRow));
}

function createUserRow(user) {
    const row = document.createElement("tr");
    const login = document.createElement("td");
    login.textContent = user.username;

    const nameCell = document.createElement("td");
    const name = document.createElement("input");
    name.value = user.displayName;
    name.maxLength = 150;
    nameCell.append(name);

    const roleCell = document.createElement("td");
    const role = document.createElement("select");
    roles.forEach(value => role.append(new Option(roleLabel(value), value, false, value === user.role)));
    roleCell.append(role);

    const departmentCell = document.createElement("td");
    const department = document.createElement("select");
    department.append(...departmentOptions(user.departmentId));
    departmentCell.append(department);

    const state = document.createElement("td");
    state.textContent = user.active ? "Ativo" : "Inativo";

    const actions = document.createElement("td");
    actions.className = "table-actions";
    const changes = () => ({ displayName: name.value, role: role.value, departmentId: toDepartmentId(department.value) });
    actions.append(
        actionButton("Salvar", "edit", () => updateUser(user, { ...changes(), active: user.active })),
        actionButton(user.active ? "Desativar" : "Ativar", "edit", () => updateUser(user, { ...changes(), active: !user.active })),
        actionButton("Redefinir senha", "cancel", () => resetPassword(user)));

    row.append(login, nameCell, roleCell, departmentCell, state, actions);
    return row;
}

async function createUser(event) {
    event.preventDefault();
    hideCreateUserMessage();
    if (!createUserForm.checkValidity()) {
        createUserForm.reportValidity();
        return showCreateUserMessage("Confira os campos obrigatórios e use uma senha com pelo menos 12 caracteres.", "danger");
    }
    const data = Object.fromEntries(new FormData(createUserForm));
    data.departmentId = toDepartmentId(data.departmentId);
    createUserButton.disabled = true;
    createUserButton.textContent = "Criando...";
    try {
        const response = await send(usersUrl, "POST", data);
        if (!response.ok) throw new Error(await errorMessage(response));
        await loadUsers();
        createUserForm.reset();
        showCreateUserMessage("Usuário criado com sucesso.", "success");
        show("Usuário criado.", "success");
    } catch (error) {
        showCreateUserMessage(error.message, "danger");
    } finally {
        createUserButton.disabled = false;
        createUserButton.textContent = "Criar usuário";
    }
}

async function updateUser(user, changes) {
    const response = await send(`${usersUrl}/${user.id}`, "PUT", changes);
    if (!response.ok) return show(await errorMessage(response), "danger");
    await loadUsers();
    show("Acesso atualizado.", "success");
}

function resetPassword(user) {
    passwordResetUserId = user.id;
    document.querySelector("#password-dialog-message").textContent = `Defina uma nova senha para ${user.username}.`;
    document.querySelector("#new-password").value = "";
    document.querySelector("#password-dialog").showModal();
}

async function submitPasswordReset(event) {
    event.preventDefault();
    const password = document.querySelector("#new-password").value;
    const response = await send(`${usersUrl}/${passwordResetUserId}/password`, "PUT", { password });
    if (!response.ok) return show(await errorMessage(response), "danger");
    document.querySelector("#password-dialog").close();
    show("Senha redefinida.", "success");
}

// ---- Helpers ----

function toDepartmentId(value) { return value ? Number(value) : null; }
function send(url, method, body) { return fetch(url, { method, headers: { "Content-Type": "application/json", [csrfToken.headerName]: csrfToken.token }, body: JSON.stringify(body) }); }
function actionButton(label, variant, action) { const button = document.createElement("button"); button.className = `table-action table-action-${variant}`; button.type = "button"; button.textContent = label; button.addEventListener("click", action); return button; }
async function logout() { await fetch("/logout", { method: "POST", headers: { [csrfToken.headerName]: csrfToken.token } }); window.location.assign("/login.html"); }
function roleLabel(role) { return { ADMIN: "Administrador", REQUESTER: "Solicitante", BUYER: "Comprador", MANAGER: "Gestor" }[role]; }
async function errorMessage(response) { const body = await response.json().catch(() => null); return body?.errors ? Object.values(body.errors).join(" ") : body?.detail ?? "Não foi possível concluir a operação."; }
function show(message, variant) { feedback.className = `operation-feedback operation-feedback-${variant}`; feedback.textContent = message; feedback.hidden = false; window.setTimeout(() => feedback.hidden = true, 3200); }
function showCreateUserMessage(message, variant) { createUserMessage.textContent = message; createUserMessage.className = `form-message full-width${variant === "success" ? " form-message-success" : ""}`; createUserMessage.hidden = false; }
function hideCreateUserMessage() { createUserMessage.hidden = true; }

start().catch(() => window.location.assign("/login.html"));
