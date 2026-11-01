package com.huanchengfly.tieba.post.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata

/**
 * Where a page goes when the window has room for two: lists on the left, what they open on
 * the right. Only one pane shows for now; scenes read this to lay out more.
 */
enum class PaneRole { List, Detail }

object PaneRoleKey : NavMetadataKey<PaneRole>

fun paneRole(role: PaneRole): Map<String, Any> = metadata { put(PaneRoleKey, role) }

val NavEntry<*>.paneRole: PaneRole?
    get() = metadata[PaneRoleKey]
