package dev.switchnat.app.config

import android.content.Context
import android.content.SharedPreferences
import dev.switchnat.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

interface PreferenceStore {
    fun contains(key: String): Boolean
    fun getString(key: String, fallback: String = ""): String
    fun getInt(key: String, fallback: Int = 0): Int
    fun putString(key: String, value: String)
}

class AndroidPreferenceStore(private val preferences: SharedPreferences) : PreferenceStore {
    override fun contains(key: String): Boolean = preferences.contains(key)
    override fun getString(key: String, fallback: String): String =
        preferences.getString(key, fallback) ?: fallback
    override fun getInt(key: String, fallback: Int): Int = preferences.getInt(key, fallback)
    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

class ProfileRepository(
    private val store: PreferenceStore,
    private val defaultTargets: () -> String,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun load(): ProfileLibrary {
        val saved = store.getString(KEY_LIBRARY_JSON).takeIf(String::isNotBlank)
            ?.let { runCatching { decode(it) }.getOrNull() }
        if (saved != null) return saved
        return persist(if (store.contains(KEY_SERVER_HOST)) migrateLegacy() else createInitialLibrary())
    }

    fun defaultUdpTargets(): String = defaultTargets().trim()

    fun saveProfile(profile: ServerProfile): ProfileLibrary {
        require(profile.id.isNotBlank()) { "Profile id is required" }
        require(profile.name.isNotBlank()) { "Profile name is required" }
        val errors = profile.config.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val current = load()
        val existing = current.profiles.firstOrNull { it.id == profile.id }
            ?: throw IllegalArgumentException("Profile not found: ${profile.id}")
        require(!existing.isSubscriptionManaged) { "Subscription nodes are read-only" }
        return persist(current.copy(profiles = current.profiles.map {
            if (it.id == profile.id) profile.copy(name = profile.name.trim()) else it
        }))
    }

    fun selectProfile(id: String): ProfileLibrary {
        val current = load()
        require(current.profiles.any { it.id == id }) { "Profile not found: $id" }
        return persist(current.copy(selectedProfileId = id))
    }

    fun createProfile(): ProfileLibrary {
        val current = load()
        val id = uniqueId(current.profiles.map { it.id }.toSet())
        val profile = ServerProfile(id, "New node", blankConfig())
        return persist(current.copy(profiles = current.profiles + profile, selectedProfileId = id))
    }

    fun renameProfile(id: String, name: String): ProfileLibrary {
        require(name.isNotBlank()) { "Profile name is required" }
        val current = load()
        val existing = current.profiles.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Profile not found: $id")
        require(!existing.isSubscriptionManaged) { "Subscription nodes are read-only" }
        return persist(current.copy(profiles = current.profiles.map {
            if (it.id == id) it.copy(name = name.trim()) else it
        }))
    }

    fun deleteProfile(id: String, activeProfileId: String = ""): ProfileLibrary {
        val current = load()
        require(current.profiles.size > 1) { "The only profile cannot be deleted" }
        require(id != activeProfileId) { "The active profile cannot be deleted" }
        val existing = current.profiles.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Profile not found: $id")
        require(!existing.isSubscriptionManaged) { "Delete the subscription instead" }
        val remaining = current.profiles.filterNot { it.id == id }
        val selected = if (current.selectedProfileId == id) remaining.first().id else current.selectedProfileId
        return persist(current.copy(profiles = remaining, selectedProfileId = selected))
    }

    fun saveStunServers(servers: List<StunServerEndpoint>): ProfileLibrary {
        val ids = mutableSetOf<String>()
        servers.forEach { server ->
            val errors = server.validate()
            require(errors.isEmpty()) { errors.joinToString("; ") }
            require(ids.add(server.id)) { "Duplicate STUN server id: ${server.id}" }
        }
        return persist(load().copy(stunServers = servers))
    }

    fun saveThemeMode(themeMode: String): ProfileLibrary {
        require(themeMode in ProfileLibrary.SUPPORTED_THEME_MODES) {
            "Unsupported theme mode: $themeMode"
        }
        return persist(load().copy(themeMode = themeMode))
    }

    fun syncSubscription(
        requestedId: String,
        name: String,
        url: String,
        nodes: List<SubscriptionNode>
    ): ProfileLibrary {
        require(name.isNotBlank()) { "Subscription name is required" }
        validateSubscriptionUrl(url)
        require(nodes.isNotEmpty()) { "Subscription has no supported nodes" }
        require(nodes.map { it.nodeKey }.toSet().size == nodes.size) {
            "Subscription contains duplicate nodes"
        }
        nodes.forEach { node ->
            require(node.nodeKey.isNotBlank()) { "Subscription node key is required" }
            require(node.name.isNotBlank()) { "Subscription node name is required" }
            val errors = node.config.validate()
            require(errors.isEmpty()) { errors.joinToString("; ") }
        }

        val current = load()
        val existingSource = requestedId.takeIf(String::isNotBlank)?.let { id ->
            current.subscriptions.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Subscription not found: $id")
        }
        val usedIds = current.profiles.mapTo(mutableSetOf()) { it.id }
        usedIds += current.subscriptions.map { it.id }
        val sourceId = existingSource?.id ?: uniqueId(usedIds).also(usedIds::add)
        val existingProfiles = current.profiles
            .filter { it.subscriptionId == sourceId }
            .associateBy { it.subscriptionNodeKey }
        val imported = nodes.map { node ->
            val profileId = existingProfiles[node.nodeKey]?.id
                ?: uniqueId(usedIds).also(usedIds::add)
            ServerProfile(
                id = profileId,
                name = node.name.trim(),
                config = node.config,
                subscriptionId = sourceId,
                subscriptionNodeKey = node.nodeKey
            )
        }
        val profiles = current.profiles.filterNot { it.subscriptionId == sourceId } + imported
        val currentSelection = current.selectedProfile
        val selectedId = current.selectedProfileId.takeIf { selected ->
            currentSelection.config.serverHost.isNotBlank() &&
                profiles.any { it.id == selected }
        } ?: imported.first().id
        val source = SubscriptionSource(
            id = sourceId,
            name = name.trim(),
            url = url.trim(),
            updatedAtMillis = nowMillis(),
            nodeCount = imported.size
        )
        val subscriptions = if (existingSource == null) {
            current.subscriptions + source
        } else {
            current.subscriptions.map { if (it.id == sourceId) source else it }
        }
        return persist(
            current.copy(
                profiles = profiles,
                selectedProfileId = selectedId,
                subscriptions = subscriptions
            )
        )
    }

    fun deleteSubscription(id: String, activeProfileId: String = ""): ProfileLibrary {
        val current = load()
        require(current.subscriptions.any { it.id == id }) { "Subscription not found: $id" }
        val removedIds = current.profiles
            .filter { it.subscriptionId == id }
            .mapTo(mutableSetOf()) { it.id }
        require(activeProfileId !in removedIds) {
            "The active node belongs to this subscription"
        }
        var profiles = current.profiles.filterNot { it.subscriptionId == id }
        if (profiles.isEmpty()) {
            profiles = listOf(ServerProfile(uniqueId(removedIds), "New node", blankConfig()))
        }
        val selectedId = current.selectedProfileId.takeIf { selected ->
            profiles.any { it.id == selected }
        } ?: profiles.first().id
        return persist(
            current.copy(
                profiles = profiles,
                selectedProfileId = selectedId,
                subscriptions = current.subscriptions.filterNot { it.id == id }
            )
        )
    }

    private fun createInitialLibrary(): ProfileLibrary {
        val profile = ServerProfile(nextId(), "New node", blankConfig())
        return ProfileLibrary(
            profiles = listOf(profile),
            selectedProfileId = profile.id,
            stunServers = defaultStunServers()
        )
    }

    private fun migrateLegacy(): ProfileLibrary {
        val config = ShadowsocksConfig(
            serverHost = store.getString(KEY_SERVER_HOST),
            serverPort = store.getInt(KEY_SERVER_PORT),
            password = store.getString(KEY_PASSWORD),
            method = store.getString(KEY_METHOD, "aes-128-gcm"),
            httpProxyPort = store.getInt(KEY_HTTP_PROXY_PORT, 7890),
            udpTargets = store.getString(KEY_UDP_TARGETS)
        )
        val id = nextId()
        val name = if (config.serverHost.isBlank()) "Saved server"
            else "${config.serverHost}:${config.serverPort}"
        return ProfileLibrary(
            profiles = listOf(ServerProfile(id, name, config)),
            selectedProfileId = id,
            stunServers = defaultStunServers()
        )
    }

    private fun blankConfig() = ShadowsocksConfig(
        serverHost = "",
        serverPort = 8388,
        password = "",
        method = "aes-128-gcm",
        httpProxyPort = 7890,
        udpTargets = defaultUdpTargets()
    )

    private fun defaultStunServers(): List<StunServerEndpoint> = listOf(
        StunServerEndpoint(nextId(), "stun.cloudflare.com", 3478),
        StunServerEndpoint(nextId(), "stun.l.google.com", 19302),
        StunServerEndpoint(nextId(), "stun1.l.google.com", 19302),
        StunServerEndpoint(nextId(), "global.stun.twilio.com", 3478)
    )

    private fun persist(library: ProfileLibrary): ProfileLibrary {
        validateLibrary(library)
        store.putString(KEY_LIBRARY_JSON, encode(library))
        return library
    }

    private fun validateLibrary(library: ProfileLibrary) {
        require(library.schemaVersion == ProfileLibrary.CURRENT_SCHEMA_VERSION) {
            "Unsupported profile schema: ${library.schemaVersion}"
        }
        require(library.themeMode in ProfileLibrary.SUPPORTED_THEME_MODES) {
            "Unsupported theme mode: ${library.themeMode}"
        }
        require(library.profiles.isNotEmpty()) { "At least one profile is required" }
        require(library.profiles.map { it.id }.toSet().size == library.profiles.size) {
            "Duplicate profile id"
        }
        require(library.profiles.any { it.id == library.selectedProfileId }) {
            "Selected profile does not exist"
        }
        require(library.subscriptions.map { it.id }.toSet().size == library.subscriptions.size) {
            "Duplicate subscription id"
        }
        val subscriptionIds = library.subscriptions.mapTo(mutableSetOf()) { it.id }
        library.profiles.filter(ServerProfile::isSubscriptionManaged).forEach { profile ->
            require(profile.subscriptionId in subscriptionIds) {
                "Subscription node has no source"
            }
            require(profile.subscriptionNodeKey.isNotBlank()) {
                "Subscription node key is required"
            }
        }
        require(
            library.profiles.filter(ServerProfile::isSubscriptionManaged)
                .map { it.subscriptionId to it.subscriptionNodeKey }
                .toSet().size ==
                library.profiles.count(ServerProfile::isSubscriptionManaged)
        ) { "Duplicate subscription node key" }
    }

    private fun validateSubscriptionUrl(raw: String) {
        val uri = runCatching { URI(raw.trim()) }.getOrNull()
        require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "A valid HTTP(S) subscription URL is required"
        }
    }

    private fun uniqueId(existing: Set<String>): String {
        repeat(100) {
            val candidate = nextId()
            if (candidate.isNotBlank() && candidate !in existing) return candidate
        }
        error("Unable to generate a unique profile id")
    }

    private fun encode(library: ProfileLibrary): String = JSONObject().apply {
        put("schemaVersion", library.schemaVersion)
        put("selectedProfileId", library.selectedProfileId)
        put("themeMode", library.themeMode)
        put("profiles", JSONArray().apply {
            library.profiles.forEach { put(JSONObject(it.toMap())) }
        })
        put("stunServers", JSONArray().apply {
            library.stunServers.forEach { put(JSONObject(it.toMap())) }
        })
        put("subscriptions", JSONArray().apply {
            library.subscriptions.forEach { put(JSONObject(it.toMap())) }
        })
    }.toString()

    private fun decode(raw: String): ProfileLibrary {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion", 0) in 1..ProfileLibrary.CURRENT_SCHEMA_VERSION) {
            "Unsupported profile schema"
        }
        val usedProfileIds = mutableSetOf<String>()
        val profiles = root.getJSONArray("profiles").objects().map { json ->
            val requestedId = json.optString("id")
            val id = if (requestedId.isNotBlank() && usedProfileIds.add(requestedId)) {
                requestedId
            } else {
                uniqueId(usedProfileIds).also(usedProfileIds::add)
            }
            val config = ShadowsocksConfig.fromMap(
                (json.optJSONObject("config") ?: JSONObject()).toStringMap()
            )
            val name = json.optString("name").trim().ifBlank {
                if (config.serverHost.isBlank()) "Saved server"
                else "${config.serverHost}:${config.serverPort}"
            }
            ServerProfile(
                id = id,
                name = name,
                config = config,
                subscriptionId = json.optString("subscriptionId"),
                subscriptionNodeKey = json.optString("subscriptionNodeKey")
            )
        }
        require(profiles.isNotEmpty()) { "At least one profile is required" }
        val requestedSelected = root.optString("selectedProfileId")
        val selected = profiles.firstOrNull { it.id == requestedSelected }?.id ?: profiles.first().id
        val usedStunIds = mutableSetOf<String>()
        val stunServers = root.optJSONArray("stunServers")?.objects().orEmpty().map { json ->
            val requestedId = json.optString("id")
            val id = if (requestedId.isNotBlank() && usedStunIds.add(requestedId)) {
                requestedId
            } else {
                uniqueId(usedStunIds).also(usedStunIds::add)
            }
            StunServerEndpoint(
                id = id,
                host = json.optString("host"),
                port = json.optInt("port"),
                enabled = json.optBoolean("enabled", true)
            )
        }
        val usedSubscriptionIds = mutableSetOf<String>()
        val subscriptions = root.optJSONArray("subscriptions")?.objects().orEmpty().map { json ->
            val requestedId = json.optString("id")
            val id = if (requestedId.isNotBlank() && usedSubscriptionIds.add(requestedId)) {
                requestedId
            } else {
                uniqueId(usedSubscriptionIds).also(usedSubscriptionIds::add)
            }
            SubscriptionSource(
                id = id,
                name = json.optString("name").trim().ifBlank { "Subscription" },
                url = json.optString("url"),
                updatedAtMillis = json.optLong("updatedAtMillis"),
                nodeCount = json.optInt("nodeCount")
            )
        }
        val validSubscriptionIds = subscriptions.mapTo(mutableSetOf()) { it.id }
        val normalizedProfiles = profiles.map { profile ->
            if (profile.subscriptionId in validSubscriptionIds) profile
            else profile.copy(subscriptionId = "", subscriptionNodeKey = "")
        }
        return ProfileLibrary(
            schemaVersion = ProfileLibrary.CURRENT_SCHEMA_VERSION,
            profiles = normalizedProfiles,
            selectedProfileId = selected,
            stunServers = stunServers,
            subscriptions = subscriptions,
            themeMode = root.optString("themeMode")
                .takeIf { it in ProfileLibrary.SUPPORTED_THEME_MODES }
                ?: ProfileLibrary.THEME_SYSTEM
        )
    }

    companion object {
        const val KEY_LIBRARY_JSON = "profileLibraryJson"
        private const val KEY_SERVER_HOST = "serverHost"
        private const val KEY_SERVER_PORT = "serverPort"
        private const val KEY_PASSWORD = "password"
        private const val KEY_METHOD = "method"
        private const val KEY_HTTP_PROXY_PORT = "httpProxyPort"
        private const val KEY_UDP_TARGETS = "udpTargets"
    }
}

fun Context.profileRepository(): ProfileRepository = ProfileRepository(
    store = AndroidPreferenceStore(getSharedPreferences("switchnat_config", Context.MODE_PRIVATE)),
    defaultTargets = {
        resources.openRawResource(R.raw.default_udp_targets).bufferedReader().use { it.readText().trim() }
    }
)

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) add(getJSONObject(index))
}

private fun JSONObject.toStringMap(): Map<String, Any?> = keys().asSequence()
    .associateWith { key -> get(key).takeUnless { it === JSONObject.NULL } }
