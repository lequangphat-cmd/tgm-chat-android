/*
 * TGM Chat - Android Client
 *
 * SPDX-FileCopyrightText: 2026 WAM
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.tgm

import androidx.compose.ui.graphics.Color
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType

/**
 * TGM look and behaviour shared with the TGM Chat web client (tgm_theme and tgm-spreed),
 * kept in one place so upstream merges only touch the call sites marked "TGM:".
 */
object TgmUi {
    /**
     * Phát 12/11/2026: Danh bạ, Lịch, Tệp tin, Nhiệm vụ stay as extras, in the app as the same web
     * pages so they are always in sync with the web. Attachments the web cannot send (location,
     * phone contact) are hidden so app and web offer the same features.
     */
    const val WEB_TABS_ENABLED = true
    const val HIDE_APP_ONLY_ATTACHMENTS = true

    /** Unread counters, like the web conversation list. */
    val unreadRed = Color(0xFFEF3B3B)

    /** Light theme bubbles, same values as tgm_theme/css/tgm-talk.css. */
    val incomingBubble = Color(0xFFFFFFFF)
    val incomingBubbleBorder = Color(0xFFE8EDF3)
    val outgoingBubble = Color(0xFFD7EBFF)
    val outgoingBubbleBorder = Color(0xFFC5DFF6)
    val mentionAllBubble = Color(0xFFFFF4CC)
    val mentionAllBubbleBorder = Color(0xFFF3E2A2)
    val chatCanvas = Color(0xFFF7F8FA)

    /** Housekeeping lines that staff do not need to read, hidden from the chat. */
    private val QUIET_SYSTEM_MESSAGES = setOf(
        SystemMessageType.CONVERSATION_CREATED,
        SystemMessageType.AVATAR_SET,
        SystemMessageType.AVATAR_REMOVED,
        SystemMessageType.CLEARED_CHAT,
        SystemMessageType.DESCRIPTION_SET,
        SystemMessageType.DESCRIPTION_REMOVED
    )

    fun isQuietSystemMessage(type: SystemMessageType?): Boolean = type != null && type in QUIET_SYSTEM_MESSAGES

    /** System lines never used as the conversation list preview; call messages stay. */
    fun isHiddenPreview(type: SystemMessageType?): Boolean =
        type != null && type != SystemMessageType.DUMMY && !type.name.startsWith("CALL_")

    private val TITLED_NAME = Regex("""^\p{Lu}{2,4}\.\s+(\S.*)$""")

    /** "OM. Linh Châu" -> "Linh Châu": staff names carry a job title prefix. */
    fun shortName(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        return TITLED_NAME.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed
    }
}
