package dev.switchnat.app.vpn

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStartupRunnerTest {

    @Test
    fun `startup task runs on named background thread`() {
        val callerThread = Thread.currentThread()
        val completed = CountDownLatch(1)
        var taskThread: Thread? = null

        VpnStartupRunner().launch {
            taskThread = Thread.currentThread()
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertNotEquals(callerThread, taskThread)
        assertTrue(taskThread?.name == "SwitchNAT-Startup")
    }
}
