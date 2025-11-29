package kz.qwertukg

import kotlinx.serialization.Serializable

@Serializable
data class Board(
    val id: Long,
    val name: String,
    val targetBranch: String = "main"
)

@Serializable
data class Column(
    val id: Long,
    val boardId: Long,
    val name: String,
    val order: Int,
    val agentId: Long? = null,
    val system: Boolean = false
)

@Serializable
data class Agent(
    val id: Long,
    val name: String,
    val roleInstructions: String = "",
    val acceptanceCriteria: String = ""
)

@Serializable
data class ChatMessage(
    val author: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class TaskStatus { OPEN, IN_PROGRESS, CLOSED }

@Serializable
data class Task(
    val id: Long,
    val boardId: Long,
    var columnId: Long,
    val title: String,
    val description: String = "",
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var branchName: String? = null,
    var status: TaskStatus = TaskStatus.OPEN
)

@Serializable
data class Settings(
    val openAiKey: String? = null,
    val repositoryUrl: String? = null,
    val targetBranch: String = "main"
)

@Serializable
data class GitEvent(
    val action: String,
    val branch: String,
    val target: String
)
