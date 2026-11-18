/*
 * TGM Chat - Android Client
 *
 * SPDX-FileCopyrightText: 2026 WAM
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.tgm

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
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
import com.nextcloud.talk.settings.SettingsActivity
import com.nextcloud.talk.data.user.model.User

/**
 * TGM: bottom navigation like the TGM Chat web menu. "Tin nhắn" is the native chat;
 * the other tabs show the same web pages inside the app ([TgmWebActivity]), so their
 * data is always the same as on the web.
 */
enum class TgmTab(
    @StringRes val label: Int,
    val icon: ImageVector,
    val path: String?,
    val enabled: Boolean = true
) {
    CHAT(R.string.tgm_tab_chat, Icons.AutoMirrored.Outlined.Chat, null),
    CONTACTS(R.string.tgm_tab_contacts, Icons.Outlined.Contacts, "/apps/contacts/"),
    // Off (Phát 18/11): only contract features in the app; Lịch is not in the contract.
    CALENDAR(R.string.tgm_tab_calendar, Icons.Outlined.CalendarMonth, "/apps/calendar/", enabled = false),
    FILES(R.string.tgm_tab_files, Icons.Outlined.Folder, "/apps/files/"),
    // Off while the server has neither Tasks nor Deck: the web menu hides "Nhiệm vụ" then too.
    TASKS(R.string.tgm_tab_tasks, Icons.Outlined.TaskAlt, "/apps/tasks/", enabled = false),

    // Native app settings (account, notifications, theme), like "Cài đặt" in the web menu.
    SETTINGS(R.string.tgm_tab_settings, Icons.Outlined.Settings, null);

    companion object {
        val visible: List<TgmTab> get() = entries.filter { it.enabled }
    }
}

@Composable
fun TgmBottomNav(selected: TgmTab, user: User?, onSelect: ((TgmTab) -> Unit)? = null) {
    val context = LocalContext.current
    NavigationBar {
        TgmTab.visible.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = {
                    if (tab == selected) return@NavigationBarItem
                    if (tab == TgmTab.SETTINGS) {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                        return@NavigationBarItem
                    }
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
