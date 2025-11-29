package kz.qwertukg

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class InMemoryStore {
    private val boards = mutableMapOf<Long, Board>()
    private val columns = mutableMapOf<Long, Column>()
    private val agents = mutableMapOf<Long, Agent>()
    private val tasks = mutableMapOf<Long, Task>()
    private val gitEvents = mutableListOf<GitEvent>()
    private var settings: Settings = Settings()

    private val boardId = AtomicLong(1)
    private val columnId = AtomicLong(1)
    private val agentId = AtomicLong(1)
    private val taskId = AtomicLong(1)

    private val mutex = Mutex()

    suspend fun createBoard(name: String, targetBranch: String?): Board = mutex.withLock {
        val id = boardId.getAndIncrement()
        val board = Board(id, name, targetBranch ?: settings.targetBranch)
        boards[id] = board
        val open = Column(columnId.getAndIncrement(), id, "Open", 0, system = true)
        val closed = Column(columnId.getAndIncrement(), id, "Closed", 9999, system = true)
        columns[open.id] = open
        columns[closed.id] = closed
        board
    }

    suspend fun updateBoard(id: Long, name: String, targetBranch: String?): Board? = mutex.withLock {
        val existing = boards[id] ?: return@withLock null
        val updated = existing.copy(name = name, targetBranch = targetBranch ?: existing.targetBranch)
        boards[id] = updated
        updated
    }

    suspend fun deleteBoard(id: Long) = mutex.withLock {
        boards.remove(id)
        columns.values.removeIf { it.boardId == id }
        tasks.values.removeIf { it.boardId == id }
    }

    suspend fun listBoards(): List<Board> = mutex.withLock { boards.values.sortedBy { it.id } }
    suspend fun getBoard(id: Long): Board? = mutex.withLock { boards[id] }

    suspend fun createColumn(boardId: Long, name: String, agentId: Long?, order: Int?): Column? = mutex.withLock {
        if (!boards.containsKey(boardId)) return@withLock null
        val id = columnId.getAndIncrement()
        val column = Column(id, boardId, name, order ?: (columns.values.filter { it.boardId == boardId }.maxOfOrNull { it.order }?.plus(1) ?: 1), agentId = agentId, system = false)
        columns[id] = column
        column
    }

    suspend fun updateColumn(id: Long, name: String?, agentId: Long?, order: Int?): Column? = mutex.withLock {
        val existing = columns[id] ?: return@withLock null
        val updated = existing.copy(
            name = name ?: existing.name,
            agentId = agentId ?: existing.agentId,
            order = order ?: existing.order
        )
        columns[id] = updated
        updated
    }

    suspend fun listColumns(boardId: Long): List<Column> = mutex.withLock {
        columns.values.filter { it.boardId == boardId }.sortedBy { it.order }
    }

    suspend fun createAgent(name: String, roleInstructions: String, acceptanceCriteria: String): Agent = mutex.withLock {
        val id = agentId.getAndIncrement()
        val agent = Agent(id, name, roleInstructions, acceptanceCriteria)
        agents[id] = agent
        agent
    }

    suspend fun updateAgent(id: Long, name: String, roleInstructions: String, acceptanceCriteria: String): Agent? = mutex.withLock {
        val existing = agents[id] ?: return@withLock null
        val updated = existing.copy(name = name, roleInstructions = roleInstructions, acceptanceCriteria = acceptanceCriteria)
        agents[id] = updated
        updated
    }

    suspend fun deleteAgent(id: Long) = mutex.withLock {
        agents.remove(id)
        val updated = columns.mapValues { (_, column) ->
            if (column.agentId == id) column.copy(agentId = null) else column
        }
        columns.clear()
        columns.putAll(updated.values.associateBy { it.id })
    }

    suspend fun listAgents(): List<Agent> = mutex.withLock { agents.values.sortedBy { it.id } }
    suspend fun getAgent(id: Long): Agent? = mutex.withLock { agents[id] }

    suspend fun createTask(boardId: Long, columnId: Long, title: String, description: String): Task? = mutex.withLock {
        val board = boards[boardId] ?: return@withLock null
        val column = columns[columnId] ?: return@withLock null
        if (column.boardId != boardId) return@withLock null
        val id = taskId.getAndIncrement()
        val task = Task(id, boardId, columnId, title, description, mutableListOf())
        tasks[id] = task
        if (column.name.equals("Open", true)) {
            task.branchName = createBranchName(task, board)
            task.messages.add(ChatMessage("Git", "Создана фича-ветка ${task.branchName} из ${board.targetBranch}"))
            gitEvents.add(GitEvent("create", task.branchName!!, board.targetBranch))
        }
        task
    }

    suspend fun updateTask(id: Long, title: String, description: String): Task? = mutex.withLock {
        val task = tasks[id] ?: return@withLock null
        val updated = task.copy(title = title, description = description)
        updated.messages += task.messages
        updated.branchName = task.branchName
        updated.status = task.status
        tasks[id] = updated
        updated
    }

    suspend fun listTasks(boardId: Long): List<Task> = mutex.withLock {
        tasks.values.filter { it.boardId == boardId }.sortedBy { it.id }
    }

    suspend fun moveTask(taskId: Long, newColumnId: Long): Task? = mutex.withLock {
        val task = tasks[taskId] ?: return@withLock null
        val newColumn = columns[newColumnId] ?: return@withLock null
        if (task.boardId != newColumn.boardId) return@withLock null
        val previousColumn = columns[task.columnId]
        task.columnId = newColumn.id
        val board = boards[task.boardId]
        if (newColumn.name.equals("Closed", true)) {
            task.status = TaskStatus.CLOSED
            task.messages.add(ChatMessage("Git", "Фича-ветка ${task.branchName ?: "feature"} слита в ${board?.targetBranch ?: "main"}"))
            task.branchName?.let { gitEvents.add(GitEvent("merge", it, board?.targetBranch ?: "main")) }
        } else if (newColumn.name.equals("Open", true)) {
            task.status = TaskStatus.OPEN
        } else {
            task.status = TaskStatus.IN_PROGRESS
        }

        newColumn.agentId?.let { agentId ->
            val agent = agents[agentId]
            if (agent != null) {
                val acceptance = agent.acceptanceCriteria.ifBlank { null }
                val acceptable = acceptance == null || task.description.contains(acceptance, ignoreCase = true)
                if (!acceptable && previousColumn != null) {
                    task.columnId = previousColumn.id
                    task.status = if (previousColumn.name.equals("Open", true)) TaskStatus.OPEN else TaskStatus.IN_PROGRESS
                    task.messages.add(ChatMessage(agent.name, "Критерии приемки не выполнены: ${agent.acceptanceCriteria}"))
                    return@withLock task
                }
                task.messages.add(
                    ChatMessage(agent.name, "Работа над задачей на этапе '${newColumn.name}'. Инструкции: ${agent.roleInstructions}")
                )
            }
        }
        task
    }

    suspend fun addMessage(taskId: Long, author: String, content: String): Task? = mutex.withLock {
        val task = tasks[taskId] ?: return@withLock null
        task.messages.add(ChatMessage(author, content))
        task
    }

    suspend fun updateSettings(newSettings: Settings): Settings = mutex.withLock {
        settings = newSettings
        settings
    }

    suspend fun getSettings(): Settings = mutex.withLock { settings }

    suspend fun getGitEvents(): List<GitEvent> = mutex.withLock { gitEvents.toList() }

    private fun createBranchName(task: Task, board: Board): String {
        val slug = task.title.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
        return "feature/${task.id}-${if (slug.isBlank()) "task" else slug.take(40)}"
    }
}
