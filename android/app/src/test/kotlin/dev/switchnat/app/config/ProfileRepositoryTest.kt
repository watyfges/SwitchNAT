package dev.switchnat.app.config

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {
    @Test
    fun bundledTargetsContainAcceptedRuleAndEndpointCounts() {
        val raw = File("src/main/res/raw/default_udp_targets.txt").readText()

        assertEquals(516, raw.lineSequence().count { it.isNotBlank() })
        assertEquals(573, ShadowsocksConfig.parseUdpTargets(raw).size)
    }

    @Test
    fun newLibraryUsesEmbeddedTargetsAndGlobalStunPresets() {
        val repository = repository(FakePreferenceStore())

        val library = repository.load()

        assertEquals("profile-1", library.selectedProfileId)
        assertEquals(DEFAULT_TARGETS, library.profiles.single().config.udpTargets)
        assertEquals(4, library.stunServers.size)
        assertTrue(library.stunServers.all { it.enabled })
    }

    @Test
    fun migrationPreservesEveryLegacyConfigFieldIncludingBlankTargets() {
        val store = FakePreferenceStore().apply {
            putString("serverHost", "203.0.113.10")
            putInt("serverPort", 8388)
            putString("password", "secret")
            putString("method", "aes-256-gcm")
            putInt("httpProxyPort", 7891)
            putString("udpTargets", "")
        }

        val library = repository(store).load()
        val profile = library.profiles.single()

        assertEquals("203.0.113.10:8388", profile.name)
        assertEquals("203.0.113.10", profile.config.serverHost)
        assertEquals(8388, profile.config.serverPort)
        assertEquals("secret", profile.config.password)
        assertEquals("aes-256-gcm", profile.config.method)
        assertEquals(7891, profile.config.httpProxyPort)
        assertEquals("", profile.config.udpTargets)
    }

    @Test
    fun savedLibraryRoundTripsProfilesAndGlobalStunList() {
        val store = FakePreferenceStore()
        val repository = repository(store)
        val initial = repository.load()
        val created = repository.createProfile()
        val renamed = repository.renameProfile(created.selectedProfileId, "Osaka")
        val disabledStun = renamed.stunServers.mapIndexed { index, server ->
            if (index == 0) server.copy(enabled = false) else server
        }
        repository.saveStunServers(disabledStun)
        repository.saveThemeMode(ProfileLibrary.THEME_DARK)

        val reloaded = repository(store).load()

        assertEquals(2, reloaded.profiles.size)
        assertEquals("Osaka", reloaded.selectedProfile.name)
        assertFalse(reloaded.stunServers.first().enabled)
        assertEquals(ProfileLibrary.THEME_DARK, reloaded.themeMode)
    }

    @Test
    fun olderLibrariesDefaultToSystemThemeAndRejectUnknownModes() {
        val store = FakePreferenceStore().apply {
            putString(
                ProfileRepository.KEY_LIBRARY_JSON,
                """{"schemaVersion":3,"selectedProfileId":"p1","profiles":[{"id":"p1","name":"Test","config":{}}],"stunServers":[],"subscriptions":[]}"""
            )
        }
        val repository = repository(store)

        assertEquals(ProfileLibrary.THEME_SYSTEM, repository.load().themeMode)
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveThemeMode("amoled")
        }
    }

    @Test
    fun newNodeIsBlank() {
        val repository = repository(FakePreferenceStore())

        val withBlank = repository.createProfile()
        val blank = withBlank.selectedProfile
        assertEquals("New node", blank.name)
        assertEquals("", blank.config.serverHost)
        assertEquals("", blank.config.password)
    }

    @Test
    fun activeAndOnlyProfilesCannotBeDeleted() {
        val repository = repository(FakePreferenceStore())
        val initial = repository.load()
        assertThrows(IllegalArgumentException::class.java) {
            repository.deleteProfile(initial.selectedProfileId, activeProfileId = "")
        }

        val withTwo = repository.createProfile()
        val inactiveId = withTwo.profiles.first { it.id != withTwo.selectedProfileId }.id
        assertThrows(IllegalArgumentException::class.java) {
            repository.deleteProfile(inactiveId, activeProfileId = inactiveId)
        }
    }

    @Test
    fun malformedSavedJsonFallsBackToLegacyWithoutDiscardingIt() {
        val store = FakePreferenceStore().apply {
            putString(ProfileRepository.KEY_LIBRARY_JSON, "not json")
            putString("serverHost", "198.51.100.9")
            putInt("serverPort", 443)
            putString("password", "secret")
            putString("method", "aes-128-gcm")
            putInt("httpProxyPort", 7890)
            putString("udpTargets", "198.51.100.9:8123")
        }

        val library = repository(store).load()

        assertEquals("198.51.100.9", library.selectedProfile.config.serverHost)
        assertEquals("198.51.100.9:8123", library.selectedProfile.config.udpTargets)
    }

    @Test
    fun subscriptionRefreshPreservesMatchingProfileAndReplacesRemovedNodes() {
        val store = FakePreferenceStore()
        val repository = repository(store)
        repository.load()
        val imported = repository.syncSubscription(
            requestedId = "",
            name = "Main subscription",
            url = "https://example.com/subscription",
            nodes = listOf(subscriptionNode("a", "Tokyo", "203.0.113.10"), subscriptionNode("b", "Osaka", "203.0.113.11"))
        )
        val source = imported.subscriptions.single()
        val tokyo = imported.profiles.first { it.subscriptionNodeKey == "a" }
        assertEquals(tokyo.id, imported.selectedProfileId)
        repository.selectProfile(tokyo.id)

        val refreshed = repository.syncSubscription(
            requestedId = source.id,
            name = "Main subscription",
            url = "https://example.com/subscription",
            nodes = listOf(subscriptionNode("a", "Tokyo updated", "203.0.113.20"), subscriptionNode("c", "Seoul", "203.0.113.12"))
        )

        assertEquals(tokyo.id, refreshed.selectedProfileId)
        assertEquals(tokyo.id, refreshed.profiles.first { it.subscriptionNodeKey == "a" }.id)
        assertEquals("Tokyo updated", refreshed.selectedProfile.name)
        assertFalse(refreshed.profiles.any { it.subscriptionNodeKey == "b" })
        assertEquals(2, refreshed.subscriptions.single().nodeCount)
        assertEquals(1234L, refreshed.subscriptions.single().updatedAtMillis)
        assertEquals(2, repository(store).load().profiles.count { it.isSubscriptionManaged })
    }

    @Test
    fun deletingSubscriptionRemovesManagedNodesButKeepsManualProfile() {
        val repository = repository(FakePreferenceStore())
        val initial = repository.load()
        val imported = repository.syncSubscription(
            requestedId = "",
            name = "Main subscription",
            url = "https://example.com/subscription",
            nodes = listOf(subscriptionNode("a", "Tokyo", "203.0.113.10"))
        )

        val deleted = repository.deleteSubscription(imported.subscriptions.single().id)

        assertTrue(deleted.subscriptions.isEmpty())
        assertEquals(listOf(initial.selectedProfileId), deleted.profiles.map { it.id })
        assertFalse(deleted.selectedProfile.isSubscriptionManaged)
    }

    private fun subscriptionNode(key: String, name: String, host: String) = SubscriptionNode(
        nodeKey = key,
        name = name,
        config = ShadowsocksConfig(
            serverHost = host,
            serverPort = 8388,
            password = "secret",
            method = "aes-256-gcm",
            httpProxyPort = 7890,
            udpTargets = DEFAULT_TARGETS
        )
    )

    private fun repository(store: FakePreferenceStore): ProfileRepository {
        val ids = generateSequence(1) { it + 1 }.map { "profile-$it" }.iterator()
        return ProfileRepository(
            store = store,
            defaultTargets = { DEFAULT_TARGETS },
            nextId = ids::next,
            nowMillis = { 1234L }
        )
    }

    private companion object {
        const val DEFAULT_TARGETS = "203.0.113.1:8123\n203.0.113.4/30:10025"
    }
}

private class FakePreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, Any>()

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun getString(key: String, fallback: String): String =
        values[key] as? String ?: fallback

    override fun getInt(key: String, fallback: Int): Int =
        values[key] as? Int ?: fallback

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    fun putInt(key: String, value: Int) {
        values[key] = value
    }
}
