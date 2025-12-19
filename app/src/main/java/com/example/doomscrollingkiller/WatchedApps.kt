package com.example.doomscrollingkiller

object WatchedApps {
    val packages = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically"
    )

    fun isWatched(packageName: String?): Boolean {
        return packageName != null && packageName in packages
    }
}
