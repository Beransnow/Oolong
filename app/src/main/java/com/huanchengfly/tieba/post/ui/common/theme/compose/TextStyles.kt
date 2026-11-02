package com.huanchengfly.tieba.post.ui.common.theme.compose

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The text styles the design uses (design-system.md §4.1), named after what they are for.
 * Reading text ([bodyLead], [body], [bodyCompact]) is further scaled by the app's font size setting
 * where it is shown.
 */
object TextStyles {
    /** The profile page's nickname. */
    val display = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)

    /** A thread's title on its page. */
    val threadTitle = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal)

    /** Bar titles, detail page titles. */
    val title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)

    /** The first floor's text. */
    val bodyLead = TextStyle(fontSize = 17.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal)

    /** A floor's text. */
    val body = TextStyle(fontSize = 16.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal)

    /** User names, forum names in a single column. */
    val name = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

    /** Sub-posts, summaries. */
    val bodyCompact = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)

    /** Forum names in two columns, small enough for long names. */
    val nameCompact = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    /** Buttons, settings row titles, tabs. */
    val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

    /** Meta information, settings row descriptions. */
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)

    /** Times, counts. */
    val footnote = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)

    /** Level badges, corner marks. */
    val micro = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
}
