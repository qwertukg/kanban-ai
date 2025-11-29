package kz.qwertukg

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(DefaultHeaders)
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
    }
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText("${cause.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
        }
    }
    install(AutoHeadResponse)
    configureRouting()
    routing {
        staticResources("/", "static")
    }
}
