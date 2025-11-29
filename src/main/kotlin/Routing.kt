package kz.qwertukg

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.http.content.*

fun Application.configureRouting() {
    routing {
        staticResources("/static", "/static")
        get("/") {
            call.respondRedirect("/static/index.html")
        }

        route("/api") {
            route("/boards") {
                get {
                    call.respond(InMemoryStore.listBoards())
                }
                post {
                    val input = call.receive<BoardInput>()
                    call.respond(HttpStatusCode.Created, InMemoryStore.createBoard(input))
                }
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    val view = id?.let { InMemoryStore.getBoardView(it) }
                    if (view == null) call.respond(HttpStatusCode.NotFound) else call.respond(view)
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    val input = call.receive<BoardInput>()
                    val updated = id?.let { InMemoryStore.updateBoard(it, input) }
                    if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null || !InMemoryStore.deleteBoard(id)) {
                        call.respond(HttpStatusCode.NotFound)
                    } else call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/columns") {
                get {
                    call.respond(InMemoryStore.listColumns())
                }
                post {
                    val input = call.receive<ColumnInput>()
                    call.respond(HttpStatusCode.Created, InMemoryStore.createColumn(input))
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    val input = call.receive<ColumnInput>()
                    val updated = id?.let { InMemoryStore.updateColumn(it, input) }
                    if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null || !InMemoryStore.deleteColumn(id)) {
                        call.respond(HttpStatusCode.BadRequest, "Нельзя удалить колонку или не найдена")
                    } else call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/tasks") {
                get {
                    call.respond(InMemoryStore.listTasks())
                }
                post {
                    val input = call.receive<TaskInput>()
                    call.respond(HttpStatusCode.Created, InMemoryStore.createTask(input))
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    val input = call.receive<TaskUpdate>()
                    val updated = id?.let { InMemoryStore.updateTask(it, input) }
                    if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null || !InMemoryStore.deleteTask(id)) {
                        call.respond(HttpStatusCode.NotFound)
                    } else call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/agents") {
                get {
                    call.respond(InMemoryStore.listAgents())
                }
                post {
                    val input = call.receive<AgentInput>()
                    call.respond(HttpStatusCode.Created, InMemoryStore.createAgent(input))
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    val input = call.receive<AgentInput>()
                    val updated = id?.let { InMemoryStore.updateAgent(it, input) }
                    if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null || !InMemoryStore.deleteAgent(id)) {
                        call.respond(HttpStatusCode.NotFound)
                    } else call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/settings") {
                get {
                    call.respond(InMemoryStore.settings)
                }
                put {
                    val settings = call.receive<Settings>()
                    InMemoryStore.settings = settings
                    call.respond(settings)
                }
            }
        }
    }
}
