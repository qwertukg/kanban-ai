interface Agent {
  id: number;
  name: string;
  roleInstructions: string;
  acceptanceCriteria: string;
}

interface Column {
  id: number;
  name: string;
  order: number;
  agentId?: number | null;
}

interface Board {
  id: number;
  name: string;
  targetBranch: string;
  repoPath: string;
  columns: Column[];
}

interface ChatMessage {
  id: number;
  author: string;
  content: string;
  timestamp: string;
}

interface Task {
  id: number;
  boardId: number;
  title: string;
  description: string;
  columnId: number;
  branchName: string;
  merged: boolean;
  gitLog: string[];
  messages: ChatMessage[];
}

const api = async <T>(url: string, options: RequestInit = {}): Promise<T> => {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
};

const agentsList = document.getElementById("agents") as HTMLUListElement;
const instructionsList = document.getElementById("instructions") as HTMLUListElement;
const boardsDiv = document.getElementById("boards") as HTMLDivElement;
const tasksDiv = document.getElementById("tasks") as HTMLDivElement;

const renderAgents = async () => {
  const agents = await api<Agent[]>("/api/agents");
  agentsList.innerHTML = agents
    .map(
      (a) =>
        `<li class="card"><strong>${a.name}</strong><div class="tag">Роль: ${a.roleInstructions}</div><div class="tag">Приемка: ${a.acceptanceCriteria}</div></li>`
    )
    .join("");
};

const renderInstructions = async () => {
  const instructions = await api<{ id: number; content: string }[]>("/api/global-instructions");
  instructionsList.innerHTML = instructions.map((i) => `<li class="card">${i.content}</li>`).join("");
};

const renderBoards = async () => {
  const boards = await api<Board[]>("/api/boards");
  boardsDiv.innerHTML = boards
    .map(
      (b) => `
      <div class="card">
        <h3>${b.name}</h3>
        <div class="tag">Целевая ветка: ${b.targetBranch}</div>
        <div class="tag">Git: ${b.repoPath}</div>
        <div>Колонки: ${b.columns.map((c) => `${c.name}${c.agentId ? ` (агент #${c.agentId})` : ""}`).join(", ")}</div>
      </div>`
    )
    .join("");
};

const renderTasks = async () => {
  const tasks = await api<Task[]>("/api/tasks");
  tasksDiv.innerHTML = tasks
    .map((t) => {
      const messageHtml = t.messages
        .map((m) => `<div><strong>${m.author}</strong>: ${m.content} <small>${new Date(m.timestamp).toLocaleTimeString()}</small></div>`) // prettier-ignore
        .join("");
      const gitHtml = t.gitLog.map((g) => `<span class="tag">${g}</span>`).join("");
      return `
      <div class="card">
        <h3>${t.title} (#${t.id})</h3>
        <div>${t.description}</div>
        <div>Колонка: ${t.columnId} | Ветка: ${t.branchName} ${t.merged ? "(merged)" : ""}</div>
        <div>${gitHtml}</div>
        <div class="messages">${messageHtml}</div>
      </div>`;
    })
    .join("");
};

(document.getElementById("agent-form") as HTMLFormElement).addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.target as HTMLFormElement;
  const formData = new FormData(form);
  await api("/api/agents", {
    method: "POST",
    body: JSON.stringify({
      name: formData.get("name"),
      roleInstructions: formData.get("roleInstructions"),
      acceptanceCriteria: formData.get("acceptanceCriteria"),
    }),
  });
  form.reset();
  renderAgents();
});

(document.getElementById("instruction-form") as HTMLFormElement).addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.target as HTMLFormElement;
  const formData = new FormData(form);
  await api("/api/global-instructions", {
    method: "POST",
    body: JSON.stringify({ content: formData.get("content") }),
  });
  form.reset();
  renderInstructions();
});

(document.getElementById("board-form") as HTMLFormElement).addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.target as HTMLFormElement;
  const formData = new FormData(form);
  await api("/api/boards", {
    method: "POST",
    body: JSON.stringify({
      name: formData.get("name"),
      targetBranch: formData.get("targetBranch") || "main",
      repoPath: formData.get("repoPath") || ".",
    }),
  });
  form.reset();
  renderBoards();
});

(document.getElementById("column-form") as HTMLFormElement).addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.target as HTMLFormElement;
  const formData = new FormData(form);
  const boardId = Number(formData.get("boardId"));
  const agentIdRaw = formData.get("agentId")?.toString();
  const agentId = agentIdRaw ? Number(agentIdRaw) : null;
  await api(`/api/boards/${boardId}/columns`, {
    method: "POST",
    body: JSON.stringify({ name: formData.get("name"), agentId }),
  });
  form.reset();
  renderBoards();
});

(document.getElementById("task-form") as HTMLFormElement).addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.target as HTMLFormElement;
  const formData = new FormData(form);
  const boardId = Number(formData.get("boardId"));
  await api(`/api/boards/${boardId}/tasks`, {
    method: "POST",
    body: JSON.stringify({
      title: formData.get("title"),
      description: formData.get("description"),
    }),
  });
  form.reset();
  renderTasks();
});

const bootstrap = () => {
  renderAgents();
  renderInstructions();
  renderBoards();
  renderTasks();
};

bootstrap();
