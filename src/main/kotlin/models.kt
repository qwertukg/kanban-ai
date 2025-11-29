package kz.qwertukg

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Agent(
    val id: Long,
    val name: String,
    val roleInstructions: String = "",
    val acceptanceCriteria: String = ""
)

@Serializable
data class GlobalInstruction(
    val id: Long,
    val content: String
)

@Serializable
data class Column(
    val id: Long,
    val name: String,
    val order: Int,
    val agentId: Long? = null
)

@Serializable
data class Board(
    val id: Long,
    val name: String,
    val targetBranch: String = "main",
    val repoPath: String = ".",
    val columns: List<Column>
)

@Serializable
data class ChatMessage(
    val id: Long,
    val author: String,
    val content: String,
    val timestamp: String = Instant.now().toString()
)

@Serializable
data class Task(
    val id: Long,
    val boardId: Long,
    val title: String,
    val description: String = "",
    val columnId: Long,
    val branchName: String,
    val merged: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val gitLog: List<String> = emptyList()
)
