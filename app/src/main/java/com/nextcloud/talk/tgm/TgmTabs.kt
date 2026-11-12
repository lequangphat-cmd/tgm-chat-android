/*
 * TGM Chat - Android Client
 *
 * SPDX-FileCopyrightText: 2026 WAM
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.tgm

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User

/**
 * TGM: bottom navigation like the TGM Chat web menu. "Tin nhắn" is the native chat;
 * the other tabs show the same web pages inside the app ([TgmWebActivity]), so their
 * data is always the same as on the web.
 */
enum class TgmTab(@StringRes val label: Int, val icon: ImageVector, val path: String?) {
    CHAT(R.string.tgm_tab_chat, Icons.AutoMirrored.Outlined.Chat, null),
    CONTACTS(R.string.tgm_tab_contacts, Icons.Outlined.Contacts, "/apps/contacts/"),
    CALENDAR(R.string.tgm_tab_calendar, Icons.Outlined.CalendarMonth, "/apps/calendar/"),
    FILES(R.string.tgm_tab_files, Icons.Outlined.Folder, "/apps/files/"),
    TASKS(R.string.tgm_tab_tasks, Icons.Outlined.TaskAlt, "/apps/deck/")
}

@Composable
fun TgmBottomNav(selected: TgmTab, user: User?, onSelect: ((TgmTab) -> Unit)? = null) {
    val context = LocalContext.current
    NavigationBar {
        TgmTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = {
                    if (tab == selected) return@NavigationBarItem
                    if (onSelect != null) {
                        onSelect(tab)
                    } else if (user != null) {
                        TgmWebActivity.open(context, user, tab)
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    }
}
