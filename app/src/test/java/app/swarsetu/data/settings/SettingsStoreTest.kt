package app.swarsetu.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips [SettingsStore] over an in-memory Preferences DataStore.
 * No Android framework or file system is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    private fun TestScope.newStore(): SettingsStore {
        return SettingsStore(InMemoryDataStore())
    }

    private class InMemoryDataStore(initialPreferences: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val _data = MutableStateFlow(initialPreferences)
        override val data: Flow<Preferences> = _data.asStateFlow()
        private val mutex = Mutex()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return mutex.withLock {
                val current = _data.value
                val updated = transform(current)
                _data.value = updated
                updated
            }
        }
    }

    @Test
    fun `name and status default to empty`() =
        runTest {
            val store = newStore()
            assertEquals("", store.displayName.first())
            assertEquals("", store.status.first())
        }

    @Test
    fun `setProfile persists both name and status`() =
        runTest {
            val store = newStore()
            store.setProfile(name = "Ada", status = "hello mesh")
            assertEquals("Ada", store.displayName.first())
            assertEquals("hello mesh", store.status.first())
        }

    @Test
    fun `individual name and status setters persist`() =
        runTest {
            val store = newStore()
            store.setDisplayName("Grace")
            store.setStatus("offline")
            assertEquals("Grace", store.displayName.first())
            assertEquals("offline", store.status.first())
        }

    @Test
    fun `block adds node id and device tag, unblock removes both`() =
        runTest {
            val store = newStore()
            store.block("node-a", deviceTag = "tag-a")
            store.block("node-b", deviceTag = null)

            assertEquals(setOf("node-a", "node-b"), store.blockedNodeIds.first())
            assertEquals(setOf("tag-a"), store.blockedDeviceTags.first())

            store.unblock("node-a", deviceTag = "tag-a")
            assertEquals(setOf("node-b"), store.blockedNodeIds.first())
            assertTrue(store.blockedDeviceTags.first().isEmpty())
        }

    @Test
    fun `last-read watermarks are keyed per conversation and read back as a stripped map`() =
        runTest {
            val store = newStore()
            store.setLastReadAt("nearby", 100L)
            store.setLastReadAt("node-x", 250L)

            assertEquals(mapOf("nearby" to 100L, "node-x" to 250L), store.lastReadAll.first())
            assertEquals(250L, store.lastReadAt("node-x").first())
            assertEquals(0L, store.lastReadAt("never-read").first())
        }

    @Test
    fun `own avatar hash sets and clears back to null`() =
        runTest {
            val store = newStore()
            assertNull(store.ownAvatarHash.first())
            store.setOwnAvatarHash("abc123")
            assertEquals("abc123", store.ownAvatarHash.first())
            store.clearOwnAvatarHash()
            assertNull(store.ownAvatarHash.first())
        }

    @Test
    fun `content filtering defaults on and can be toggled off`() =
        runTest {
            val store = newStore()
            assertTrue(store.contentFilteringEnabled.first())
            store.setContentFilteringEnabled(false)
            assertEquals(false, store.contentFilteringEnabled.first())
        }

    @Test
    fun `mesh enabled defaults on and round-trips off then back on`() =
        runTest {
            val store = newStore()
            assertTrue(store.meshEnabled.first())
            store.setMeshEnabled(false)
            assertEquals(false, store.meshEnabled.first())
            store.setMeshEnabled(true)
            assertTrue(store.meshEnabled.first())
        }

    @Test
    fun `profile version and avatar timestamp round-trip`() =
        runTest {
            val store = newStore()
            assertEquals(0L, store.profileVersion.first())
            store.setProfileVersion(7L)
            store.setAvatarUpdatedAt(4242L)
            assertEquals(7L, store.profileVersion.first())
            assertEquals(4242L, store.avatarUpdatedAt.first())
        }

    @Test
    fun `recordReviewAttempt stamps the time and increments the lifetime count`() =
        runTest {
            val store = newStore()
            store.recordReviewAttempt(now = 1_000L)
            store.recordReviewAttempt(now = 2_000L)
            assertEquals(2_000L, store.reviewLastAttemptAt.first())
            assertEquals(2L, store.reviewAttemptCount.first())
        }

    @Test
    fun `clearReviewState resets engagement, attempt time, and count`() =
        runTest {
            val store = newStore()
            store.setReviewEngagementStartedAt(500L)
            store.recordReviewAttempt(now = 1_000L)

            store.clearReviewState()

            assertEquals(0L, store.reviewEngagementStartedAt.first())
            assertEquals(0L, store.reviewLastAttemptAt.first())
            assertEquals(0L, store.reviewAttemptCount.first())
        }

    @Test
    fun `the Internet plane is off with no spools until something configures it`() =
        runTest {
            val store = newStore()
            assertEquals(false, store.spoolEnabled.first())
            assertEquals(emptySet<String>(), store.spoolUrls.first())
        }

    @Test
    fun `default spools seed once and never come back after the user removes one`() =
        runTest {
            val store = newStore()
            val default = "wss://lax.spool.getknit.app/spool/v1"

            store.seedDefaultSpools(listOf(default))
            assertEquals(setOf(default), store.spoolUrls.first())
            // Seeding a spool must not switch the plane on — the two decisions are separate.
            assertEquals(false, store.spoolEnabled.first())

            store.removeSpoolUrl(default)
            store.seedDefaultSpools(listOf(default)) // every app start re-runs this
            assertEquals("a removed default must stay removed", emptySet<String>(), store.spoolUrls.first())
        }

    @Test
    fun `seeding preserves a spool the user added and tolerates an empty default list`() =
        runTest {
            val store = newStore()
            store.addSpoolUrl("wss://mine.example/spool/v1")

            store.seedDefaultSpools(listOf("wss://lax.spool.getknit.app/spool/v1"))
            assertEquals(
                setOf("wss://mine.example/spool/v1", "wss://lax.spool.getknit.app/spool/v1"),
                store.spoolUrls.first(),
            )

            val bare = newStore()
            bare.seedDefaultSpools(emptyList())
            assertEquals(emptySet<String>(), bare.spoolUrls.first())
        }
}
