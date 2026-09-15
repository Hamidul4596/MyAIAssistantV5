package com.myaiaassistant.v4.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Handles the "open app / search web" actions listed in the V4 feature list.
 * Deliberately narrow: it only launches apps by known package name or opens
 * URLs, it never runs shell commands.
 */
object AppLauncher {

    // A small, explicit allow-list. Add more (package name) pairs as needed.
    private val KNOWN_APPS = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps"
    )

    /** Returns true if it launched something locally (no AI call needed). */
    fun tryHandleLocally(context: Context, rawText: String): Boolean {
        val text = rawText.trim().lowercase()

        // "X খুলো" / "open X" -> try known app first
        for ((keyword, packageName) in KNOWN_APPS) {
            if (text.contains(keyword)) {
                return launchApp(context, packageName) || openWebFallback(context, keyword)
            }
        }

        // "... search করো" / "search for ..." -> Google search
        if (text.contains("search")) {
            val query = rawText.substringAfter("search", "").trim().ifBlank { rawText }
            return openWebSearch(context, query)
        }

        return false
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun openWebFallback(context: Context, query: String): Boolean =
        openWebSearch(context, query)

    private fun openWebSearch(context: Context, query: String): Boolean {
        return try {
            val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
