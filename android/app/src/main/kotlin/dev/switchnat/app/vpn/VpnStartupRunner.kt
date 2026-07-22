package dev.switchnat.app.vpn

class VpnStartupRunner {
    fun launch(task: () -> Unit): Thread {
        return Thread(task, THREAD_NAME).also {
            it.isDaemon = true
            it.start()
        }
    }

    private companion object {
        const val THREAD_NAME = "SwitchNAT-Startup"
    }
}
