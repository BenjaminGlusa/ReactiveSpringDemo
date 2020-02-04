package reservations.frontend

/*
Taken from Sébastien Deleuze
Source:
https://github.com/sdeleuze/spring-messenger/blob/step-4-kotlin-js/frontend/src/main/kotlin/messenger/frontend/Extensions.kt
 */

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.w3c.dom.EventSource

@ExperimentalCoroutinesApi
fun EventSource.asFlow() = callbackFlow {
    onmessage = {
        offer(it.data as String)
    }
    onerror = {
        cancel(CancellationException("EventSource failed"))
    }
    awaitClose {
        this@asFlow.close()
    }
}