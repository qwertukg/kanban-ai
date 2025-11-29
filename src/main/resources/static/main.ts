interface Board { id: number; name: string; description: string; targetBranch: string; }
interface Column { id: number; boardId: number; name: string; order: number; agentId?: number | null; systemType?: string | null; }
interface Agent { id: number; name: string; roleInstructions: string; acceptanceCriteria: string; globalInstructions: string; }
interface Task { id: number; boardId: number; columnId: number; title: string; description: string; branchName?: string | null; chat: ChatMessage[]; }
interface ChatMessage { author: string; message: string; timestamp: number; }
interface Settings { apiKey: string; repository: string; defaultBranch: string; globalAgentInstructions: string; }
interface BoardView { board: Board; columns: Column[]; tasks: Task[]; agents: Agent[]; }

let currentBoardId: number | null = null;

const boardList = document.getElementById("boardList") as HTMLDivElement;
const boardView = document.getElementById("boardView") as HTMLDivElement;
const boardsView = document.getElementById("boardsView") as HTMLDivElement;
const columnsView = document.getElementById("columnsView") as HTMLDivElement;
const tasksView = document.getElementById("tasksView") as HTMLDivElement;
const agentsView = document.getElementById("agentsView") as HTMLDivElement;
const settingsView = document.getElementById("settingsView") as HTMLDivElement;

async function fetchJson<T>(url: string, options: RequestInit = {}): Promise<T> {
    const res = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...options });
    return await res.json();
}

async function loadBoards() {
    const boards = await fetchJson<Board[]>("/api/boards");
    boardList.innerHTML = "";
    boards.forEach(b => {
        const link = document.createElement("a");
        link.href = "#";
        link.className = "list-group-item list-group-item-action";
        link.innerText = b.name;
        link.onclick = () => selectBoard(b.id);
        boardList.appendChild(link);
    });
    if (boards.length > 0 && currentBoardId === null) {
        selectBoard(boards[0].id);
    }
    renderBoardsAdmin(boards);
}

async function selectBoard(id: number) {
    currentBoardId = id;
    const view = await fetchJson<BoardView>(`/api/boards/${id}`);
    renderBoard(view);
    renderColumnsAdmin(view.columns, view.board, view.agents);
    renderTasksAdmin(view.tasks, view.columns);
}

function renderBoard(view: BoardView) {
    const row = document.createElement("div");
    row.className = "row g-3";
    view.columns.sort((a, b) => a.order - b.order).forEach(col => {
        const colDiv = document.createElement("div");
        colDiv.className = "col-md-3";
        colDiv.innerHTML = `<div class="card h-100 column" data-column="${col.id}">` +
            `<div class="card-header d-flex justify-content-between align-items-center"><span>${col.name}</span>` +
            `${col.agentId ? `<span class='badge bg-secondary'>👾 ${agentName(view.agents, col.agentId)}</span>` : ""}` +
            `</div>` +
            `<div class="card-body scrollable" id="col-${col.id}"></div>` +
            `</div>`;
        const body = colDiv.querySelector('.card-body') as HTMLDivElement;
        colDiv.addEventListener('dragover', (e) => { e.preventDefault(); });
        colDiv.addEventListener('drop', (e: DragEvent) => {
            e.preventDefault();
            const taskId = e.dataTransfer?.getData('text/plain');
            if (taskId) moveTask(Number(taskId), col.id);
        });
        view.tasks.filter(t => t.columnId === col.id).forEach(task => {
            const taskCard = document.createElement("div");
            taskCard.className = "card mb-2 task-card";
            taskCard.draggable = true;
            taskCard.ondragstart = (e) => { e.dataTransfer?.setData('text/plain', task.id.toString()); };
            taskCard.innerHTML = `<div class="card-body">` +
                `<h6 class="card-title">${task.title}</h6>` +
                `<p class="card-text small">${task.description}</p>` +
                `${task.branchName ? `<span class='badge bg-info text-dark'>${task.branchName}</span>` : ""}` +
                `<div class="mt-2 small text-muted">${task.chat.slice(-2).map(m => `${m.author}: ${m.message}`).join('<br/>')}</div>` +
                `</div>`;
            body.appendChild(taskCard);
        });
        row.appendChild(colDiv);
    });
    boardView.innerHTML = `<div class="d-flex justify-content-between align-items-center mb-3">` +
        `<div><h3>${view.board.name}</h3><div class='text-muted'>Целевая ветка: ${view.board.targetBranch}</div></div>` +
        `<button class="btn btn-sm btn-primary" onclick="document.dispatchEvent(new CustomEvent('openTaskForm'))">Создать задачу</button>` +
        `</div>`;
    boardView.appendChild(row);
}

function renderBoardsAdmin(boards: Board[]) {
    boardsView.classList.remove('d-none');
    boardsView.innerHTML = `<div class="card mb-3"><div class="card-body">` +
        `<h5>Создать доску</h5>` +
        `<div class="row g-2">` +
        `<div class="col"><input id="boardName" class="form-control" placeholder="Название" /></div>` +
        `<div class="col"><input id="boardDesc" class="form-control" placeholder="Описание" /></div>` +
        `<div class="col"><input id="boardBranch" class="form-control" placeholder="Целевая ветка" value="main" /></div>` +
        `<div class="col-auto"><button class="btn btn-success" id="boardCreate">Создать</button></div>` +
        `</div></div></div>`;
    const table = document.createElement('table');
    table.className = 'table table-striped';
    table.innerHTML = `<thead><tr><th>Название</th><th>Описание</th><th>Ветка</th><th></th></tr></thead><tbody></tbody>`;
    boards.forEach(b => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${b.name}</td><td>${b.description}</td><td>${b.targetBranch}</td>` +
            `<td><button class='btn btn-sm btn-outline-primary me-1' data-id='${b.id}'>Изменить</button>` +
            `<button class='btn btn-sm btn-outline-danger' data-del='${b.id}'>Удалить</button></td>`;
        table.querySelector('tbody')?.appendChild(tr);
    });
    boardsView.appendChild(table);
    (document.getElementById('boardCreate') as HTMLButtonElement).onclick = async () => {
        const name = (document.getElementById('boardName') as HTMLInputElement).value;
        const description = (document.getElementById('boardDesc') as HTMLInputElement).value;
        const targetBranch = (document.getElementById('boardBranch') as HTMLInputElement).value || 'main';
        await fetch('/api/boards', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, description, targetBranch }) });
        await loadBoards();
    };
    table.querySelectorAll('button[data-id]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.id);
            const name = prompt('Новое имя?');
            const description = prompt('Новое описание?') || '';
            const targetBranch = prompt('Целевая ветка?') || 'main';
            if (name) {
                await fetch(`/api/boards/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, description, targetBranch }) });
                await loadBoards();
            }
        });
    });
    table.querySelectorAll('button[data-del]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.del);
            await fetch(`/api/boards/${id}`, { method: 'DELETE' });
            await loadBoards();
        });
    });
}

function agentName(agents: Agent[], agentId: number) {
    return agents.find(a => a.id === agentId)?.name || 'И.И.';
}

function renderColumnsAdmin(columns: Column[], board: Board, agents: Agent[]) {
    columnsView.innerHTML = `<div class='card mb-3'><div class='card-body'>` +
        `<h5>Создать колонку</h5>` +
        `<div class='row g-2'>` +
        `<div class='col'><input id='colName' class='form-control' placeholder='Название'/></div>` +
        `<div class='col'><input id='colOrder' type='number' class='form-control' placeholder='Порядок'/></div>` +
        `<div class='col'><select id='colAgent' class='form-select'><option value=''>Без агента</option>${agents.map(a => `<option value='${a.id}'>${a.name}</option>`).join('')}</select></div>` +
        `<div class='col-auto'><button class='btn btn-success' id='createCol'>Создать</button></div>` +
        `</div></div></div>`;
    const table = document.createElement('table');
    table.className = 'table table-hover';
    table.innerHTML = `<thead><tr><th>Колонка</th><th>Порядок</th><th>Агент</th><th>Системная</th><th></th></tr></thead><tbody></tbody>`;
    columns.forEach(c => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${c.name}</td><td>${c.order}</td><td>${c.agentId ? agentName(agents, c.agentId) : ''}</td><td>${c.systemType ?? ''}</td>` +
            `<td>${c.systemType ? '' : `<button class='btn btn-sm btn-outline-primary me-1' data-id='${c.id}'>Изменить</button>`}
            ${c.systemType ? '' : `<button class='btn btn-sm btn-outline-danger' data-del='${c.id}'>Удалить</button>`}</td>`;
        table.querySelector('tbody')?.appendChild(tr);
    });
    columnsView.appendChild(table);
    (document.getElementById('createCol') as HTMLButtonElement).onclick = async () => {
        const name = (document.getElementById('colName') as HTMLInputElement).value;
        const order = Number((document.getElementById('colOrder') as HTMLInputElement).value || 1);
        const agentIdValue = (document.getElementById('colAgent') as HTMLSelectElement).value;
        await fetch('/api/columns', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ boardId: board.id, name, order, agentId: agentIdValue || null }) });
        await selectBoard(board.id);
    };
    table.querySelectorAll('button[data-id]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.id);
            const name = prompt('Название колонки?');
            const order = Number(prompt('Порядок?') || '1');
            const agentId = prompt('ID агента или оставить пустым?');
            if (name) {
                await fetch(`/api/columns/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ boardId: board.id, name, order, agentId: agentId ? Number(agentId) : null }) });
                await selectBoard(board.id);
            }
        });
    });
    table.querySelectorAll('button[data-del]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.del);
            await fetch(`/api/columns/${id}`, { method: 'DELETE' });
            await selectBoard(board.id);
        });
    });
}

function renderTasksAdmin(tasks: Task[], columns: Column[]) {
    tasksView.innerHTML = `<div class='card mb-3'><div class='card-body'>` +
        `<h5>Создать задачу</h5>` +
        `<div class='row g-2'>` +
        `<div class='col'><input id='taskTitle' class='form-control' placeholder='Название'/></div>` +
        `<div class='col'><input id='taskDesc' class='form-control' placeholder='Описание'/></div>` +
        `<div class='col'><select id='taskColumn' class='form-select'>${columns.map(c => `<option value='${c.id}'>${c.name}</option>`).join('')}</select></div>` +
        `<div class='col-auto'><button class='btn btn-success' id='taskCreate'>Создать</button></div>` +
        `</div></div></div>`;
    const table = document.createElement('table');
    table.className = 'table table-sm table-bordered';
    table.innerHTML = `<thead><tr><th>Задача</th><th>Колонка</th><th>Ветка</th><th>Чат</th><th></th></tr></thead><tbody></tbody>`;
    tasks.forEach(t => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${t.title}</td><td>${columns.find(c => c.id === t.columnId)?.name ?? ''}</td><td>${t.branchName ?? ''}</td>` +
            `<td class='small'>${t.chat.slice(-2).map(m => `${m.author}: ${m.message}`).join('<br/>')}</td>` +
            `<td><button class='btn btn-sm btn-outline-primary me-1' data-id='${t.id}'>Переместить</button>` +
            `<button class='btn btn-sm btn-outline-danger' data-del='${t.id}'>Удалить</button></td>`;
        table.querySelector('tbody')?.appendChild(tr);
    });
    tasksView.appendChild(table);
    (document.getElementById('taskCreate') as HTMLButtonElement).onclick = async () => {
        const title = (document.getElementById('taskTitle') as HTMLInputElement).value;
        const description = (document.getElementById('taskDesc') as HTMLInputElement).value;
        const columnId = Number((document.getElementById('taskColumn') as HTMLSelectElement).value);
        if (!currentBoardId) return;
        await fetch('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ boardId: currentBoardId, columnId, title, description }) });
        await selectBoard(currentBoardId);
    };
    table.querySelectorAll('button[data-id]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.id);
            const columnId = Number(prompt('Новый columnId?') || '');
            const note = prompt('Комментарий для чата?') || '';
            await fetch(`/api/tasks/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ columnId, note }) });
            if (currentBoardId) await selectBoard(currentBoardId);
        });
    });
    table.querySelectorAll('button[data-del]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.del);
            await fetch(`/api/tasks/${id}`, { method: 'DELETE' });
            if (currentBoardId) await selectBoard(currentBoardId);
        });
    });
}

function renderAgentsAdmin(agents: Agent[]) {
    agentsView.innerHTML = `<div class='card mb-3'><div class='card-body'>` +
        `<h5>Создать агента</h5>` +
        `<div class='row g-2'>` +
        `<div class='col'><input id='agentName' class='form-control' placeholder='Имя'/></div>` +
        `<div class='col'><input id='agentRole' class='form-control' placeholder='Роль'/></div>` +
        `<div class='col'><input id='agentCriteria' class='form-control' placeholder='Критерии приемки'/></div>` +
        `<div class='col'><input id='agentGlobal' class='form-control' placeholder='Личные инструкции'/></div>` +
        `<div class='col-auto'><button class='btn btn-success' id='agentCreate'>Создать</button></div>` +
        `</div></div></div>`;
    const table = document.createElement('table');
    table.className = 'table table-striped';
    table.innerHTML = `<thead><tr><th>Имя</th><th>Роль</th><th>Критерии</th><th>Инструкции</th><th></th></tr></thead><tbody></tbody>`;
    agents.forEach(a => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${a.name}</td><td>${a.roleInstructions}</td><td>${a.acceptanceCriteria}</td><td>${a.globalInstructions}</td>` +
            `<td><button class='btn btn-sm btn-outline-primary me-1' data-id='${a.id}'>Изменить</button>` +
            `<button class='btn btn-sm btn-outline-danger' data-del='${a.id}'>Удалить</button></td>`;
        table.querySelector('tbody')?.appendChild(tr);
    });
    agentsView.appendChild(table);
    (document.getElementById('agentCreate') as HTMLButtonElement).onclick = async () => {
        const name = (document.getElementById('agentName') as HTMLInputElement).value;
        const roleInstructions = (document.getElementById('agentRole') as HTMLInputElement).value;
        const acceptanceCriteria = (document.getElementById('agentCriteria') as HTMLInputElement).value;
        const globalInstructions = (document.getElementById('agentGlobal') as HTMLInputElement).value;
        await fetch('/api/agents', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, roleInstructions, acceptanceCriteria, globalInstructions }) });
        await refreshAgents();
        if (currentBoardId) await selectBoard(currentBoardId);
    };
    table.querySelectorAll('button[data-id]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.id);
            const name = prompt('Имя?');
            const roleInstructions = prompt('Роль?') || '';
            const acceptanceCriteria = prompt('Критерии?') || '';
            const globalInstructions = prompt('Инструкции?') || '';
            if (name) {
                await fetch(`/api/agents/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, roleInstructions, acceptanceCriteria, globalInstructions }) });
                await refreshAgents();
                if (currentBoardId) await selectBoard(currentBoardId);
            }
        });
    });
    table.querySelectorAll('button[data-del]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const id = Number((btn as HTMLButtonElement).dataset.del);
            await fetch(`/api/agents/${id}`, { method: 'DELETE' });
            await refreshAgents();
            if (currentBoardId) await selectBoard(currentBoardId);
        });
    });
}

async function refreshAgents() {
    const agents = await fetchJson<Agent[]>("/api/agents");
    renderAgentsAdmin(agents);
}

async function renderSettings() {
    const settings = await fetchJson<Settings>("/api/settings");
    settingsView.innerHTML = `<div class='card'><div class='card-body'>` +
        `<h5>Настройки</h5>` +
        `<div class='row g-2 mb-2'>` +
        `<div class='col-md-6'><label class='form-label'>API Key</label><input id='setKey' class='form-control' value='${settings.apiKey}' /></div>` +
        `<div class='col-md-6'><label class='form-label'>Репозиторий</label><input id='setRepo' class='form-control' value='${settings.repository}' /></div>` +
        `<div class='col-md-6'><label class='form-label'>Целевая ветка</label><input id='setBranch' class='form-control' value='${settings.defaultBranch}' /></div>` +
        `<div class='col-md-6'><label class='form-label'>Глобальные инструкции агентам</label><textarea id='setGlobal' class='form-control'>${settings.globalAgentInstructions}</textarea></div>` +
        `</div>` +
        `<button class='btn btn-primary' id='saveSettings'>Сохранить</button>` +
        `</div></div>`;
    (document.getElementById('saveSettings') as HTMLButtonElement).onclick = async () => {
        const apiKey = (document.getElementById('setKey') as HTMLInputElement).value;
        const repository = (document.getElementById('setRepo') as HTMLInputElement).value;
        const defaultBranch = (document.getElementById('setBranch') as HTMLInputElement).value;
        const globalAgentInstructions = (document.getElementById('setGlobal') as HTMLTextAreaElement).value;
        await fetch('/api/settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ apiKey, repository, defaultBranch, globalAgentInstructions }) });
    };
}

async function moveTask(taskId: number, columnId: number) {
    await fetch(`/api/tasks/${taskId}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ columnId }) });
    if (currentBoardId) await selectBoard(currentBoardId);
}

function setupMenu() {
    document.querySelectorAll('[data-view]').forEach(el => {
        el.addEventListener('click', (event) => {
            event.preventDefault();
            const target = (event.currentTarget as HTMLElement).dataset.view || '';
            [boardsView, columnsView, tasksView, agentsView, settingsView].forEach(v => v.classList.add('d-none'));
            document.getElementById(target)?.classList.remove('d-none');
            if (target === 'agentsView') refreshAgents();
            if (target === 'settingsView') renderSettings();
            if (target === 'boardsView') loadBoards();
            if (target === 'columnsView' && currentBoardId) selectBoard(currentBoardId);
            if (target === 'tasksView' && currentBoardId) selectBoard(currentBoardId);
        });
    });
}

document.addEventListener('openTaskForm', () => {
    const title = prompt('Название задачи?');
    const description = prompt('Описание?') || '';
    if (!currentBoardId) return;
    if (title) {
        // default to first column
        fetchJson<BoardView>(`/api/boards/${currentBoardId}`).then(view => {
            const target = view.columns.find(c => c.systemType === 'OPEN') || view.columns[0];
            fetch('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ boardId: currentBoardId, columnId: target.id, title, description }) }).then(() => selectBoard(currentBoardId!));
        });
    }
});

setupMenu();
loadBoards();
refreshAgents();
renderSettings();
