package dev.switchnat.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPermissionCoordinatorTest {

    @Test
    fun `already prepared completes immediately without launching`() {
        val launched = mutableListOf<String>()
        val coordinator = VpnPermissionCoordinator(
            prepare = { null },
            launchForResult = launched::add
        )
        var granted: Boolean? = null

        val outcome = coordinator.request { granted = it }

        assertEquals(RequestOutcome.ALREADY_GRANTED, outcome)
        assertEquals(true, granted)
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `approved activity result completes pending request`() {
        val launched = mutableListOf<String>()
        val coordinator = VpnPermissionCoordinator(
            prepare = { "vpn-consent" },
            launchForResult = launched::add
        )
        var granted: Boolean? = null

        val outcome = coordinator.request { granted = it }

        assertEquals(RequestOutcome.LAUNCHED, outcome)
        assertEquals(listOf("vpn-consent"), launched)
        assertNull(granted)

        coordinator.complete(granted = true)

        assertEquals(true, granted)
    }

    @Test
    fun `denied activity result completes pending request`() {
        val coordinator = VpnPermissionCoordinator(
            prepare = { "vpn-consent" },
            launchForResult = { }
        )
        var granted: Boolean? = null

        coordinator.request { granted = it }
        coordinator.complete(granted = false)

        assertEquals(false, granted)
    }

    @Test
    fun `second request is rejected while result is pending`() {
        val coordinator = VpnPermissionCoordinator(
            prepare = { "vpn-consent" },
            launchForResult = { }
        )
        var firstResult: Boolean? = null
        var secondResult: Boolean? = null

        val firstOutcome = coordinator.request { firstResult = it }
        val secondOutcome = coordinator.request { secondResult = it }

        assertEquals(RequestOutcome.LAUNCHED, firstOutcome)
        assertEquals(RequestOutcome.BUSY, secondOutcome)
        assertNull(firstResult)
        assertNull(secondResult)

        coordinator.complete(granted = true)

        assertEquals(true, firstResult)
        assertNull(secondResult)
    }
}
