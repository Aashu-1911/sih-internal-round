package app.swarsetu

import app.swarsetu.mesh.wifiaware.NanWatchdogPolicy
import app.swarsetu.mesh.wifiaware.NanWatchdogPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [NanWatchdogPolicy] — the pure two-tier wedge episode-clock decision. */
class NanWatchdogPolicyTest {
    // Production tuning constants (WifiAwareTransport companion), passed explicitly here.
    private val responderRefreshMs = 45_000L
    private val reattachCooldownMs = 20_000L
    private val wedgeRestartMs = 180_000L
    private val episodeStart = 1_000_000L

    /** decide() with an owed, running episode by default; each test overrides the axis it exercises. */
    @Suppress("LongParameterList") // mirrors the policy's own (suppressed) parameter list
    private fun decide(
        healthy: Boolean = true,
        reachableOwed: Boolean = true,
        corroboratedOwed: Boolean = true,
        now: Long,
        syncOwedSince: Long = episodeStart,
        lastLinkOrAcceptAt: Long = 0L, // < episodeStart ⇒ no progress
        lastReattachAt: Long = 0L,
        lastRestartAt: Long = 0L,
    ) = NanWatchdogPolicy.decide(
        healthy = healthy,
        reachableOwed = reachableOwed,
        corroboratedOwed = corroboratedOwed,
        now = now,
        syncOwedSince = syncOwedSince,
        lastLinkOrAcceptAt = lastLinkOrAcceptAt,
        lastReattachAt = lastReattachAt,
        lastRestartAt = lastRestartAt,
        responderRefreshMs = responderRefreshMs,
        reattachCooldownMs = reattachCooldownMs,
        wedgeRestartMs = wedgeRestartMs,
    )

    @Test
    fun unhealthyClearsTheEpisode() {
        assertEquals(NanWatchdogPolicy.Decision(Action.None, 0L), decide(healthy = false, now = episodeStart + 200_000L))
    }

    @Test
    fun `no peers or nothing owed clears the episode and causes no restart`() {
        assertEquals(NanWatchdogPolicy.Decision(Action.None, 0L), decide(reachableOwed = false, now = episodeStart + 200_000L))
    }

    @Test
    fun `unreachable peer causes no process restart`() {
        assertEquals(NanWatchdogPolicy.Decision(Action.None, 0L), decide(reachableOwed = false, now = episodeStart + 200_000L))
    }

    @Test
    fun firstOwedTickStartsTheEpisodeAtNow() {
        val now = 1_234_567L
        assertEquals(NanWatchdogPolicy.Decision(Action.None, now), decide(now = now, syncOwedSince = 0L))
    }

    @Test
    fun aLinkSinceTheEpisodeBeganRestartsTheClock() {
        val now = episodeStart + 50_000L
        assertEquals(NanWatchdogPolicy.Decision(Action.None, now), decide(now = now, lastLinkOrAcceptAt = episodeStart))
    }

    @Test
    fun `tier-1 refresh occurs before tier-2 recovery`() {
        val now = episodeStart + responderRefreshMs // owedFor == boundary (>=)
        assertEquals(NanWatchdogPolicy.Decision(Action.RefreshResponder, episodeStart), decide(now = now))
    }

    @Test
    fun tier1DoesNotFireJustBelowTheBoundary() {
        val now = episodeStart + responderRefreshMs - 1
        assertEquals(NanWatchdogPolicy.Decision(Action.None, episodeStart), decide(now = now))
    }

    @Test
    fun tier1BlockedByCooldownHoldsWhileBelowRestart() {
        val now = episodeStart + 50_000L
        assertEquals(
            NanWatchdogPolicy.Decision(Action.None, episodeStart),
            decide(now = now, lastReattachAt = now - (reattachCooldownMs - 1)),
        )
    }

    @Test
    fun `genuine corroborated wedge triggers recovery action`() {
        val now = episodeStart + wedgeRestartMs // owedFor == restart boundary
        assertEquals(
            NanWatchdogPolicy.Decision(Action.RestartProcess, episodeStart),
            decide(now = now, lastReattachAt = now - 5_000L), // tier-1 cooldown blocks; tier-2 eligible + corroborated
        )
    }

    @Test
    fun `sync owed but not corroborated causes no process restart`() {
        val now = episodeStart + wedgeRestartMs
        assertEquals(
            NanWatchdogPolicy.Decision(Action.None, episodeStart),
            decide(now = now, corroboratedOwed = false, lastReattachAt = now - 5_000L),
        )
    }

    @Test
    fun tier2RateLimitedHolds() {
        val now = episodeStart + wedgeRestartMs
        assertEquals(
            NanWatchdogPolicy.Decision(Action.None, episodeStart),
            decide(now = now, lastReattachAt = now - 5_000L, lastRestartAt = now - (wedgeRestartMs - 1)),
        )
    }
}
