package kz.qwertukg

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class KanbanRepository {
    private val agents = mutableMapOf<Long, Agent>()
    private val boards = mutableMapOf<Long, Board>()
    private val columns = mutableMapOf<Long, Column>()
    private val tasks = mutableMapOf<Long, Task>()
    private val messages = mutableMapOf<Long, MutableList<ChatMessage>>()
    private val gitEvents = mutableMapOf<Long, MutableList<String>>()
    private val globalInstructions = mutableMapOf<Long, GlobalInstruction>()

    private val agentId = AtomicLong(1)
    private val boardId = AtomicLong(1)
    private val columnId = AtomicLong(1)
    private val taskId = AtomicLong(1)
    private val messageId = AtomicLong(1)
    private val instructionId = AtomicLong(1)

    private val mutex = Mutex()

    suspend fun createAgent(name: String, roleInstructions: String, acceptanceCriteria: String): Agent = mutex.withLock {
        val agent = Agent(agentId.getAndIncrement(), name, roleInstructions, acceptanceCriteria)
        agents[agent.id] = agent
        agent
    }

    suspend fun updateAgent(id: Long, roleInstructions: String, acceptanceCriteria: String): Agent? = mutex.withLock {
        val existing = agents[id] ?: return@withLock null
        val updated = existing.copy(roleInstructions = roleInstructions, acceptanceCriteria = acceptanceCriteria)
        agents[id] = updated
        updated
    }

    suspend fun listAgents(): List<Agent> = mutex.withLock { agents.values.sortedBy { it.id } }

    suspend fun addGlobalInstruction(content: String): GlobalInstruction = mutex.withLock {
        val instruction = GlobalInstruction(instructionId.getAndIncrement(), content)
        globalInstructions[instruction.id] = instruction
        instruction
    }

    suspend fun listGlobalInstructions(): List<GlobalInstruction> = mutex.withLock {
        globalInstructions.values.sortedBy { it.id }
    }

    suspend fun createBoard(name: String, targetBranch: String, repoPath: String): Board = mutex.withLock {
        val openColumn = Column(columnId.getAndIncrement(), "Open", 0, null)
        val closedColumn = Column(columnId.getAndIncrement(), "Closed", 99, null)
        columns[openColumn.id] = openColumn
        columns[closedColumn.id] = closedColumn
        val board = Board(boardId.getAndIncrement(), name, targetBranch, repoPath, listOf(openColumn, closedColumn))
        boards[board.id] = board
        board
    }

    suspend fun addColumn(boardId: Long, name: String, agentId: Long?): Board? = mutex.withLock {
        val board = boards[boardId] ?: return@withLock null
        val nextOrder = board.columns.maxOf { it.order } + 1
        val newColumn = Column(columnId.getAndIncrement(), name, nextOrder, agentId)
        columns[newColumn.id] = newColumn
        val updatedBoard = board.copy(columns = (board.columns + newColumn).sortedBy { it.order })
        boards[board.id] = updatedBoard
        updatedBoard
    }

    suspend fun assignAgentToColumn(boardId: Long, columnId: Long, agentId: Long?): Board? = mutex.withLock {
        val board = boards[boardId] ?: return@withLock null
        val updatedColumns = board.columns.map { column ->
            if (column.id == columnId) column.copy(agentId = agentId) else column
        }
        val updatedBoard = board.copy(columns = updatedColumns)
        updatedColumns.forEach { columns[it.id] = it }
        boards[boardId] = updatedBoard
        updatedBoard
    }

    suspend fun listBoards(): List<Board> = mutex.withLock { boards.values.sortedBy { it.id } }

    suspend fun createTask(boardId: Long, title: String, description: String): Task? = mutex.withLock {
        val board = boards[boardId] ?: return@withLock null
        val openColumn = board.columns.firstOrNull { it.name.equals("Open", ignoreCase = true) } ?: return@withLock null
        val id = taskId.getAndIncrement()
        val branch = "feature/${board.name.lowercase().replace(" ", "-")}-${id}"
        val newMessage = ChatMessage(messageId.getAndIncrement(), "system", "Создана задача и ветка $branch")
        val newTask = Task(id, boardId, title, description, openColumn.id, branchName = branch, messages = listOf(newMessage), gitLog = listOf("Создана ветка $branch от ${board.targetBranch}"))
        tasks[id] = newTask
        messages[id] = mutableListOf(newMessage)
        gitEvents[id] = mutableListOf("branch-created:$branch")
        newTask
    }

    suspend fun listTasks(boardId: Long? = null): List<Task> = mutex.withLock {
        tasks.values.filter { boardId == null || it.boardId == boardId }.sortedBy { it.id }
    }

    suspend fun addMessage(taskId: Long, author: String, content: String): Task? = mutex.withLock {
        val task = tasks[taskId] ?: return@withLock null
        val list = messages.getOrPut(taskId) { mutableListOf() }
        val message = ChatMessage(messageId.getAndIncrement(), author, content)
        list += message
        val updated = task.copy(messages = list.toList())
        tasks[taskId] = updated
        updated
    }

    suspend fun moveTask(taskId: Long, targetColumnId: Long): Task? = mutex.withLock {
        val task = tasks[taskId] ?: return@withLock null
        val board = boards[task.boardId] ?: return@withLock null
        val targetColumn = board.columns.firstOrNull { it.id == targetColumnId } ?: return@withLock null
        val gitLog = gitEvents.getOrPut(taskId) { mutableListOf() }
        val messageLog = messages.getOrPut(taskId) { mutableListOf() }

        val updatedGitLog = gitLog.toMutableList()
        val updatedMessages = messageLog.toMutableList()

        val updatedBranchName = task.branchName
        var merged = task.merged

        if (targetColumn.name.equals("Closed", ignoreCase = true)) {
            updatedGitLog += "merged:${task.branchName}->${board.targetBranch}"
            merged = true
            updatedMessages += ChatMessage(messageId.getAndIncrement(), "system", "Ветка ${task.branchName} слита в ${board.targetBranch}")
        } else if (targetColumn.name.equals("Open", ignoreCase = true) && task.columnId != targetColumn.id) {
            updatedGitLog += "reopened:${task.branchName}"
            updatedMessages += ChatMessage(messageId.getAndIncrement(), "system", "Задача возвращена в Open")
        } else {
            val assignedAgent = targetColumn.agentId?.let { agents[it] }
            if (assignedAgent != null) {
                updatedMessages += ChatMessage(
                    messageId.getAndIncrement(),
                    assignedAgent.name,
                    "Агент начал работу: ${assignedAgent.roleInstructions}"
                )
                updatedMessages += ChatMessage(
                    messageId.getAndIncrement(),
                    assignedAgent.name,
                    "Критерии приемки: ${assignedAgent.acceptanceCriteria}"
                )
            }
        }

        gitEvents[taskId] = updatedGitLog
        messages[taskId] = updatedMessages

        val updatedTask = task.copy(columnId = targetColumnId, merged = merged, gitLog = updatedGitLog.toList(), messages = updatedMessages.toList(), branchName = updatedBranchName)
        tasks[taskId] = updatedTask
        updatedTask
    }
}

val repository = KanbanRepository()
