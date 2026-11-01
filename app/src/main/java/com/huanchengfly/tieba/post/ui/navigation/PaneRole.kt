package com.huanchengfly.tieba.post.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata

/**
 * Where a page goes when the window has room for two panes: lists on the left, what they open
 * on the right, and a few pages, such as logging in, over the whole window.
 */
enum class PaneRole { List, Detail, Full }

object PaneRoleKey : NavMetadataKey<PaneRole>

fun paneRole(role: PaneRole): Map<String, Any> = metadata { put(PaneRoleKey, role) }

val NavEntry<*>.paneRole: PaneRole?
    get() = metadata[PaneRoleKey]
