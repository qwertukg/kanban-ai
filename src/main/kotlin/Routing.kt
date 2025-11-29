package kz.qwertukg

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateBoardRequest(val name: String, val targetBranch: String = "main", val repoPath: String = ".")

@Serializable
data class ColumnRequest(val name: String, val agentId: Long? = null)

@Serializable
data class AgentRequest(val name: String, val roleInstructions: String, val acceptanceCriteria: String)

@Serializable
data class UpdateAgentRequest(val roleInstructions: String, val acceptanceCriteria: String)

@Serializable
data class TaskRequest(val title: String, val description: String = "")

@Serializable
data class MoveTaskRequest(val targetColumnId: Long)

@Serializable
data class MessageRequest(val author: String, val content: String)

@Serializable
data class InstructionRequest(val content: String)

fun Application.configureRouting() {
    routing {
        route("/api") {
            route("/agents") {
                get { call.respond(repository.listAgents()) }
                post {
                    val body = call.receive<AgentRequest>()
                    val created = repository.createAgent(body.name, body.roleInstructions, body.acceptanceCriteria)
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = call.receive<UpdateAgentRequest>()
                    val updated = repository.updateAgent(id, body.roleInstructions, body.acceptanceCriteria)
                    if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                }
            }

            route("/global-instructions") {
                get { call.respond(repository.listGlobalInstructions()) }
                post {
                    val body = call.receive<InstructionRequest>()
                    val created = repository.addGlobalInstruction(body.content)
                    call.respond(HttpStatusCode.Created, created)
                }
            }

            route("/boards") {
                get { call.respond(repository.listBoards()) }
                post {
                    val request = call.receive<CreateBoardRequest>()
                    val board = repository.createBoard(request.name, request.targetBranch, request.repoPath)
                    call.respond(HttpStatusCode.Created, board)
                }

                route("/{boardId}/columns") {
                    post {
                        val boardId = call.parameters["boardId"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val body = call.receive<ColumnRequest>()
                        val updated = repository.addColumn(boardId, body.name, body.agentId)
                        if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                    }
                    put("/{columnId}/agent") {
                        val boardId = call.parameters["boardId"]?.toLongOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                        val columnId = call.parameters["columnId"]?.toLongOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                        val body = call.receive<ColumnRequest>()
                        val updated = repository.assignAgentToColumn(boardId, columnId, body.agentId)
                        if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                    }
                }

                route("/{boardId}/tasks") {
                    get {
                        val boardId = call.parameters["boardId"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                        call.respond(repository.listTasks(boardId))
                    }
                    post {
                        val boardId = call.parameters["boardId"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val body = call.receive<TaskRequest>()
                        val task = repository.createTask(boardId, body.title, body.description)
                        if (task == null) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.Created, task)
                    }
                }
            }

            route("/tasks") {
                get { call.respond(repository.listTasks()) }
                post("/{taskId}/move") {
                    val taskId = call.parameters["taskId"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val body = call.receive<MoveTaskRequest>()
                    val updated = repository.moveTask(taskId, body.targetColumnId)
                    if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                }
                post("/{taskId}/messages") {
                    val taskId = call.parameters["taskId"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val body = call.receive<MessageRequest>()
                    val updated = repository.addMessage(taskId, body.author, body.content)
                    if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                }
            }
        }
    }
}
