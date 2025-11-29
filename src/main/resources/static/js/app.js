const state = {
    boards: [],
    columns: [],
    tasks: [],
    agents: [],
    gitEvents: [],
    settingsLoaded: false,
    selectedBoardId: null
};
const boardList = document.querySelector('#board-list');
const boardTitle = document.querySelector('#board-title');
const kanban = document.querySelector('#kanban');
const gitEventsView = document.querySelector('#git-events');
async function api(path, options) {
    const response = await fetch(path, {
        headers: { 'Content-Type': 'application/json' },
        ...options
    });
    if (!response.ok) {
        throw new Error(await response.text());
    }
    return response.json();
}
async function loadBoards() {
    state.boards = await api('/api/boards');
    if (state.selectedBoardId === null && state.boards.length > 0) {
        state.selectedBoardId = state.boards[0].id;
    }
    renderBoards();
    await refreshBoardData();
}
async function refreshBoardData() {
    if (state.selectedBoardId === null)
        return;
    const [columns, tasks, agents, gitEvents] = await Promise.all([
        api(`/api/boards/${state.selectedBoardId}/columns`),
        api(`/api/boards/${state.selectedBoardId}/tasks`),
        api(`/api/agents`),
        api(`/api/git-events`)
    ]);
    state.columns = columns;
    state.tasks = tasks;
    state.agents = agents;
    state.gitEvents = gitEvents;
    renderBoard();
    renderForms();
}
function renderBoards() {
    boardList.innerHTML = '';
    const list = document.createElement('ul');
    state.boards.forEach(board => {
        const item = document.createElement('li');
        const button = document.createElement('button');
        button.textContent = board.name;
        button.className = board.id === state.selectedBoardId ? 'contrast outline' : 'outline';
        button.onclick = () => {
            state.selectedBoardId = board.id;
            refreshBoardData();
        };
        item.appendChild(button);
        list.appendChild(item);
    });
    boardList.appendChild(list);
}
function renderBoard() {
    if (state.selectedBoardId === null) {
        boardTitle.textContent = 'Выберите доску';
        kanban.innerHTML = '';
        return;
    }
    const currentBoard = state.boards.find(b => b.id === state.selectedBoardId);
    boardTitle.textContent = currentBoard ? `Доска: ${currentBoard.name}` : 'Доска';
    kanban.innerHTML = '';
    state.columns.sort((a, b) => a.order - b.order).forEach(column => {
        const columnEl = document.createElement('div');
        columnEl.className = 'column';
        columnEl.dataset.columnId = String(column.id);
        const header = document.createElement('div');
        header.className = 'column-header';
        header.innerHTML = `<strong>${column.name}</strong><small>${renderAgent(column.agentId)}</small>`;
        columnEl.appendChild(header);
        const tasksContainer = document.createElement('div');
        tasksContainer.addEventListener('dragover', e => {
            e.preventDefault();
        });
        tasksContainer.addEventListener('drop', e => {
            e.preventDefault();
            const taskId = Number(e.dataTransfer?.getData('text/plain'));
            moveTask(taskId, column.id);
        });
        state.tasks.filter(t => t.columnId === column.id).forEach(task => {
            const card = document.createElement('article');
            card.className = 'task-card';
            card.draggable = true;
            card.dataset.taskId = String(task.id);
            card.addEventListener('dragstart', e => {
                card.classList.add('dragging');
                e.dataTransfer?.setData('text/plain', String(task.id));
            });
            card.addEventListener('dragend', () => card.classList.remove('dragging'));
            card.innerHTML = `
                <strong>${task.title}</strong>
                <p>${task.description || ''}</p>
                <small>Статус: ${task.status}</small><br>
                <small>Ветка: ${task.branchName || '—'}</small>
            `;
            if (task.messages.length > 0) {
                const messages = document.createElement('div');
                task.messages.slice(-3).forEach(msg => {
                    const m = document.createElement('div');
                    m.className = 'message';
                    m.innerHTML = `<strong>${msg.author}:</strong> ${msg.content}`;
                    messages.appendChild(m);
                });
                card.appendChild(messages);
            }
            tasksContainer.appendChild(card);
        });
        columnEl.appendChild(tasksContainer);
        kanban.appendChild(columnEl);
    });
    renderGitEvents();
}
function renderGitEvents() {
    gitEventsView.innerHTML = '';
    state.gitEvents.slice().reverse().forEach(event => {
        const row = document.createElement('div');
        row.innerHTML = `<strong>${event.action}</strong>: ${event.branch} → ${event.target}`;
        gitEventsView.appendChild(row);
    });
}
function renderForms() {
    const boardSelects = [
        document.querySelector('#column-board'),
        document.querySelector('#task-board')
    ];
    boardSelects.forEach(select => {
        if (!select)
            return;
        select.innerHTML = '';
        state.boards.forEach(board => {
            const option = document.createElement('option');
            option.value = String(board.id);
            option.textContent = board.name;
            option.selected = board.id === state.selectedBoardId;
            select.appendChild(option);
        });
    });
    const columnSelect = document.querySelector('#task-column');
    if (columnSelect) {
        columnSelect.innerHTML = '';
        state.columns.forEach(col => {
            const option = document.createElement('option');
            option.value = String(col.id);
            option.textContent = `${col.name} (${col.id})`;
            columnSelect.appendChild(option);
        });
    }
    const agentSelect = document.querySelector('#column-agent');
    if (agentSelect) {
        agentSelect.innerHTML = '<option value="">-- без агента --</option>';
        state.agents.forEach(agent => {
            const option = document.createElement('option');
            option.value = String(agent.id);
            option.textContent = agent.name;
            agentSelect.appendChild(option);
        });
    }
    const agentList = document.querySelector('#agent-list');
    if (agentList) {
        agentList.innerHTML = '';
        state.agents.forEach(agent => {
            const box = document.createElement('details');
            const summary = document.createElement('summary');
            summary.textContent = `${agent.name} (#${agent.id})`;
            box.appendChild(summary);
            const role = document.createElement('p');
            role.textContent = `Роль: ${agent.roleInstructions}`;
            const acceptance = document.createElement('p');
            acceptance.textContent = `Приемка: ${agent.acceptanceCriteria}`;
            box.appendChild(role);
            box.appendChild(acceptance);
            agentList.appendChild(box);
        });
    }
}
function renderAgent(agentId) {
    if (!agentId)
        return '';
    const agent = state.agents.find(a => a.id === agentId);
    return agent ? agent.name : '';
}
async function moveTask(taskId, columnId) {
    await api(`/api/tasks/${taskId}/move`, {
        method: 'PUT',
        body: JSON.stringify({ columnId })
    });
    await refreshBoardData();
}
function bindForms() {
    document.querySelector('#refresh')?.addEventListener('click', loadBoards);
    document.querySelector('#create-board')?.addEventListener('click', async () => {
        const name = (document.querySelector('#board-name')?.value || '').trim();
        const targetBranch = (document.querySelector('#board-target')?.value || '').trim();
        if (!name)
            return;
        await api('/api/boards', { method: 'POST', body: JSON.stringify({ name, targetBranch }) });
        (document.querySelector('#board-name')).value = '';
        await loadBoards();
    });
    document.querySelector('#create-column')?.addEventListener('click', async () => {
        const boardId = Number((document.querySelector('#column-board')?.value));
        const name = (document.querySelector('#column-name')?.value || '').trim();
        const order = Number(document.querySelector('#column-order')?.value || '0');
        const agent = (document.querySelector('#column-agent')?.value || '');
        const agentId = agent ? Number(agent) : null;
        if (!name || !boardId)
            return;
        await api('/api/columns', { method: 'POST', body: JSON.stringify({ boardId, name, order, agentId }) });
        (document.querySelector('#column-name')).value = '';
        await refreshBoardData();
    });
    document.querySelector('#create-task')?.addEventListener('click', async () => {
        const boardId = Number(document.querySelector('#task-board')?.value);
        const columnId = Number(document.querySelector('#task-column')?.value);
        const title = (document.querySelector('#task-title')?.value || '').trim();
        const description = (document.querySelector('#task-description')?.value || '').trim();
        if (!title || !boardId || !columnId)
            return;
        await api('/api/tasks', { method: 'POST', body: JSON.stringify({ boardId, columnId, title, description }) });
        (document.querySelector('#task-title')).value = '';
        (document.querySelector('#task-description')).value = '';
        await refreshBoardData();
    });
    document.querySelector('#create-agent')?.addEventListener('click', async () => {
        const name = (document.querySelector('#agent-name')?.value || '').trim();
        const roleInstructions = (document.querySelector('#agent-role')?.value || '').trim();
        const acceptanceCriteria = (document.querySelector('#agent-acceptance')?.value || '').trim();
        if (!name)
            return;
        await api('/api/agents', { method: 'POST', body: JSON.stringify({ name, roleInstructions, acceptanceCriteria }) });
        (document.querySelector('#agent-name')).value = '';
        (document.querySelector('#agent-role')).value = '';
        (document.querySelector('#agent-acceptance')).value = '';
        await refreshBoardData();
    });
    document.querySelector('#save-settings')?.addEventListener('click', async () => {
        const openAiKey = (document.querySelector('#settings-key')?.value || '').trim();
        const repositoryUrl = (document.querySelector('#settings-repo')?.value || '').trim();
        const targetBranch = (document.querySelector('#settings-target')?.value || '').trim();
        await api('/api/settings', { method: 'PUT', body: JSON.stringify({ openAiKey, repositoryUrl, targetBranch }) });
        await refreshBoardData();
    });
}
bindForms();
loadBoards();
export {};
