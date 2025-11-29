package kz.qwertukg

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale

@Serializable
data class Board(
    val id: Long,
    var name: String,
    var description: String = "",
    var targetBranch: String = "main"
)

@Serializable
data class Column(
    val id: Long,
    val boardId: Long,
    var name: String,
    var order: Int,
    var agentId: Long? = null,
    val systemType: SystemColumn? = null
)

@Serializable
data class Agent(
    val id: Long,
    var name: String,
    var roleInstructions: String = "",
    var acceptanceCriteria: String = "",
    var globalInstructions: String = ""
)

@Serializable
data class ChatMessage(
    val author: String,
    val message: String,
    val timestamp: Long = Instant.now().toEpochMilli()
)

@Serializable
data class Task(
    val id: Long,
    val boardId: Long,
    var columnId: Long,
    var title: String,
    var description: String = "",
    var branchName: String? = null,
    val chat: MutableList<ChatMessage> = mutableListOf()
)

@Serializable
data class Settings(
    var apiKey: String = "",
    var repository: String = "",
    var defaultBranch: String = "main",
    var globalAgentInstructions: String = ""
)

@Serializable
data class BoardView(
    val board: Board,
    val columns: List<Column>,
    val tasks: List<Task>,
    val agents: List<Agent>
)

@Serializable
data class BoardInput(val name: String, val description: String = "", val targetBranch: String = "main")

@Serializable
data class ColumnInput(val boardId: Long, val name: String, val order: Int, val agentId: Long? = null)

@Serializable
data class TaskInput(val boardId: Long, val columnId: Long, val title: String, val description: String = "")

@Serializable
data class TaskUpdate(
    val title: String? = null,
    val description: String? = null,
    val columnId: Long? = null,
    val note: String? = null
)

@Serializable
data class AgentInput(
    val name: String,
    val roleInstructions: String = "",
    val acceptanceCriteria: String = "",
    val globalInstructions: String = ""
)

enum class SystemColumn { OPEN, CLOSED }

object InMemoryStore {
    private val boards = mutableListOf<Board>()
    private val columns = mutableListOf<Column>()
    private val tasks = mutableListOf<Task>()
    private val agents = mutableListOf<Agent>()
    private var boardSeq = 1L
    private var columnSeq = 1L
    private var taskSeq = 1L
    private var agentSeq = 1L
    var settings: Settings = Settings()

    fun listBoards(): List<Board> = boards.sortedBy { it.id }
    fun listColumns(): List<Column> = columns.sortedBy { it.order }
    fun listTasks(): List<Task> = tasks.sortedBy { it.id }
    fun listAgents(): List<Agent> = agents.sortedBy { it.id }

    fun createBoard(input: BoardInput): Board {
        val board = Board(boardSeq++, input.name, input.description, input.targetBranch)
        boards += board
        createSystemColumns(board.id)
        return board
    }

    fun updateBoard(id: Long, input: BoardInput): Board? {
        val board = boards.find { it.id == id } ?: return null
        board.name = input.name
        board.description = input.description
        board.targetBranch = input.targetBranch
        return board
    }

    fun deleteBoard(id: Long): Boolean {
        val removed = boards.removeIf { it.id == id }
        if (removed) {
            val columnIds = columns.filter { it.boardId == id }.map { it.id }
            columns.removeIf { it.boardId == id }
            tasks.removeIf { it.boardId == id || columnIds.contains(it.columnId) }
        }
        return removed
    }

    fun createColumn(input: ColumnInput): Column {
        val column = Column(columnSeq++, input.boardId, input.name, input.order, input.agentId, null)
        columns += column
        return column
    }

    fun updateColumn(id: Long, input: ColumnInput): Column? {
        val column = columns.find { it.id == id } ?: return null
        if (column.systemType != null) return column
        column.name = input.name
        column.order = input.order
        column.agentId = input.agentId
        return column
    }

    fun deleteColumn(id: Long): Boolean {
        val column = columns.find { it.id == id } ?: return false
        if (column.systemType != null) return false
        columns.remove(column)
        tasks.removeIf { it.columnId == id }
        return true
    }

    fun createTask(input: TaskInput): Task {
        val task = Task(taskSeq++, input.boardId, input.columnId, input.title, input.description)
        task.chat += ChatMessage("system", "Создана задача и помещена в колонку")
        handleColumnTransition(task, null, columns.find { it.id == task.columnId })
        tasks += task
        return task
    }

    fun updateTask(id: Long, update: TaskUpdate): Task? {
        val task = tasks.find { it.id == id } ?: return null
        val fromColumn = columns.find { it.id == task.columnId }
        update.title?.let { task.title = it }
        update.description?.let { task.description = it }
        update.columnId?.let { newColumnId ->
            val toColumn = columns.find { it.id == newColumnId }
            if (toColumn != null) {
                task.columnId = newColumnId
                handleColumnTransition(task, fromColumn, toColumn)
            }
        }
        update.note?.takeIf { it.isNotBlank() }?.let {
            task.chat += ChatMessage("system", it)
        }
        return task
    }

    fun deleteTask(id: Long): Boolean = tasks.removeIf { it.id == id }

    fun createAgent(input: AgentInput): Agent {
        val agent = Agent(
            agentSeq++,
            input.name,
            input.roleInstructions,
            input.acceptanceCriteria,
            input.globalInstructions
        )
        agents += agent
        return agent
    }

    fun updateAgent(id: Long, input: AgentInput): Agent? {
        val agent = agents.find { it.id == id } ?: return null
        agent.name = input.name
        agent.roleInstructions = input.roleInstructions
        agent.acceptanceCriteria = input.acceptanceCriteria
        agent.globalInstructions = input.globalInstructions
        return agent
    }

    fun deleteAgent(id: Long): Boolean = agents.removeIf { it.id == id }

    fun getBoardView(boardId: Long): BoardView? {
        val board = boards.find { it.id == boardId } ?: return null
        val relatedColumns = columns.filter { it.boardId == boardId }.sortedBy { it.order }
        val relatedTasks = tasks.filter { it.boardId == boardId }
        return BoardView(board, relatedColumns, relatedTasks, agents.toList())
    }

    private fun createSystemColumns(boardId: Long) {
        columns += Column(columnSeq++, boardId, "Open", order = 0, agentId = null, systemType = SystemColumn.OPEN)
        columns += Column(columnSeq++, boardId, "Closed", order = 999, agentId = null, systemType = SystemColumn.CLOSED)
    }

    private fun handleColumnTransition(task: Task, from: Column?, to: Column?) {
        if (to == null) return
        if (from?.id != to.id) {
            task.chat += ChatMessage("system", "Перемещение в колонку ${to.name}")
        }

        val board = boards.find { it.id == task.boardId }

        if (to.systemType == SystemColumn.OPEN && task.branchName == null) {
            task.branchName = generateBranchName(task)
            task.chat += ChatMessage(
                "git",
                "Создана фича-ветка ${task.branchName} от ${board?.targetBranch ?: settings.defaultBranch}"
            )
        }

        to.agentId?.let { agentId ->
            val agent = agents.find { it.id == agentId }
            val note = buildString {
                append("Агент ${agent?.name ?: agentId} принял задачу. ")
                agent?.roleInstructions?.takeIf { it.isNotBlank() }?.let { append("Роль: $it. ") }
                agent?.acceptanceCriteria?.takeIf { it.isNotBlank() }?.let { append("Критерии: $it. ") }
                settings.globalAgentInstructions.takeIf { it.isNotBlank() }?.let { append("Глобальные инструкции: ${settings.globalAgentInstructions}.") }
            }
            if (note.isNotBlank()) {
                task.chat += ChatMessage(agent?.name ?: "agent", note)
            }
        }

        if (to.systemType == SystemColumn.CLOSED && task.branchName != null) {
            task.chat += ChatMessage(
                "git",
                "Ветка ${task.branchName} слита в ${board?.targetBranch ?: settings.defaultBranch}"
            )
            task.branchName = null
        }
    }

    private fun generateBranchName(task: Task): String {
        val slug = task.title.lowercase(Locale.getDefault()).replace("[^a-z0-9]+".toRegex(), "-").trim('-')
        return "feature/${task.id}-${slug.take(20)}"
    }
}
