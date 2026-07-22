package dev.switchnat.app.bridge

enum class RequestOutcome {
    ALREADY_GRANTED,
    LAUNCHED,
    BUSY
}

class VpnPermissionCoordinator<T>(
    private val prepare: () -> T?,
    private val launchForResult: (T) -> Unit
) {
    private var pendingCompletion: ((Boolean) -> Unit)? = null

    fun request(onComplete: (Boolean) -> Unit): RequestOutcome {
        if (pendingCompletion != null) {
            return RequestOutcome.BUSY
        }

        val request = prepare()
        if (request == null) {
            onComplete(true)
            return RequestOutcome.ALREADY_GRANTED
        }

        pendingCompletion = onComplete
        launchForResult(request)
        return RequestOutcome.LAUNCHED
    }

    fun complete(granted: Boolean) {
        val completion = pendingCompletion ?: return
        pendingCompletion = null
        completion(granted)
    }
}
