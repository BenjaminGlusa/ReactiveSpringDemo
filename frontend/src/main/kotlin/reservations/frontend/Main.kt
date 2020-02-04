package reservations.frontend

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import org.w3c.dom.EventSource
import org.w3c.dom.HTMLDivElement
import kotlin.browser.document


@ExperimentalCoroutinesApi
suspend fun main() {

    val messageContainer = document.getElementById("messages") as HTMLDivElement

    EventSource("/dogs/stream").asFlow()
            .map { JSON.parse<Message>(it) }
            .collect {
                val div = document.createElement("div").apply {
                    innerHTML = "<p>${it.message}</p>"
                }
                messageContainer.appendChild(div)
            }

}

data class Message(val message: String)