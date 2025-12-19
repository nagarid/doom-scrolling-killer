package com.example.doomscrollingkiller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedAppsTest {
    @Test
    fun detectsKnownPackages() {
        assertTrue(WatchedApps.isWatched("com.instagram.android"))
        assertTrue(WatchedApps.isWatched("com.facebook.katana"))
        assertTrue(WatchedApps.isWatched("com.zhiliaoapp.musically"))
        assertFalse(WatchedApps.isWatched("com.example.other"))
        assertFalse(WatchedApps.isWatched(null))
    }
}
