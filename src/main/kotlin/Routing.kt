package kz.qwertukg

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.net.URLConnection

private val store = InMemoryStore()

fun Application.configureRouting() {
    install(DefaultHeaders)
    install(AutoHeadResponse)
    install(CallLogging)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
    }
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText(this::class.java.classLoader.getResource("static/index.html")!!.readText(), ContentType.Text.Html)
        }
        get("/static/{...}") {
            val path = call.parameters.getAll("...")?.joinToString("/")
            if (path.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val resource = this::class.java.classLoader.getResourceAsStream("static/$path")
            if (resource == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val bytes = resource.readBytes()
                call.respondBytes(bytes, contentType = contentTypeFor(path))
            }
        }
        route("/api") {
            boards()
            columns()
            tasks()
            agents()
            settings()
            get("/git-events") {
                call.respond(store.getGitEvents())
            }
        }
    }
}

private fun Route.boards() {
    route("/boards") {
        get {
            call.respond(store.listBoards())
        }
        post {
            val request = call.receive<CreateBoardRequest>()
            val created = store.createBoard(request.name, request.targetBranch)
            call.respond(HttpStatusCode.Created, created)
        }
        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            val board = id?.let { store.getBoard(it) }
            if (board == null) call.respond(HttpStatusCode.NotFound) else call.respond(board)
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val request = call.receive<CreateBoardRequest>()
            val updated = store.updateBoard(id, request.name, request.targetBranch)
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
        }
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            store.deleteBoard(id)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/{id}/columns") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(store.listColumns(id))
        }
        get("/{id}/tasks") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(store.listTasks(id))
        }
    }
}

private fun Route.columns() {
    route("/columns") {
        post {
            val request = call.receive<CreateColumnRequest>()
            val created = store.createColumn(request.boardId, request.name, request.agentId, request.order)
            if (created == null) call.respond(HttpStatusCode.BadRequest) else call.respond(HttpStatusCode.Created, created)
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val request = call.receive<UpdateColumnRequest>()
            val updated = store.updateColumn(id, request.name, request.agentId, request.order)
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
        }
    }
}

private fun Route.tasks() {
    route("/tasks") {
        post {
            val request = call.receive<CreateTaskRequest>()
            val created = store.createTask(request.boardId, request.columnId, request.title, request.description)
            if (created == null) call.respond(HttpStatusCode.BadRequest) else call.respond(HttpStatusCode.Created, created)
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val request = call.receive<UpdateTaskRequest>()
            val updated = store.updateTask(id, request.title, request.description)
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
        }
        put("/{id}/move") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val request = call.receive<MoveTaskRequest>()
            val updated = store.moveTask(id, request.columnId)
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
        }
        post("/{id}/messages") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val request = call.receive<CreateMessageRequest>()
            val updated = store.addMessage(id, request.author, request.content)
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
        }
    }
}

private fun Route.agents() {
    route("/agents") {
        get { call.respond(store.listAgents()) }
        post {
            val request = call.receive<CreateAgentRequest>()
            call.respond(HttpStatusCode.Created, store.createAgent(request.name, request.roleInstructions, request.acceptanceCriteria))
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val request = call.receive<CreateAgentRequest>()
            val updated = store.updateAgent(id, request.name, request.roleInstructions, request.acceptanceCriteria)
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
        }
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            store.deleteAgent(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun Route.settings() {
    route("/settings") {
        get { call.respond(store.getSettings()) }
        put {
            val request = call.receive<Settings>()
            call.respond(store.updateSettings(request))
        }
    }
}

@Serializable
private data class CreateBoardRequest(val name: String, val targetBranch: String? = null)

@Serializable
private data class CreateColumnRequest(val boardId: Long, val name: String, val agentId: Long? = null, val order: Int? = null)

@Serializable
private data class UpdateColumnRequest(val name: String? = null, val agentId: Long? = null, val order: Int? = null)

@Serializable
private data class CreateTaskRequest(val boardId: Long, val columnId: Long, val title: String, val description: String = "")

@Serializable
private data class UpdateTaskRequest(val title: String, val description: String)

@Serializable
private data class MoveTaskRequest(val columnId: Long)

@Serializable
private data class CreateMessageRequest(val author: String, val content: String)

@Serializable
private data class CreateAgentRequest(val name: String, val roleInstructions: String = "", val acceptanceCriteria: String = "")

private fun contentTypeFor(path: String): ContentType {
    return when {
        path.endsWith(".js") -> ContentType.Application.JavaScript
        path.endsWith(".css") -> ContentType.Text.CSS
        path.endsWith(".html") -> ContentType.Text.Html
        else -> ContentType.parse(URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream")
    }
}
