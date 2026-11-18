/*
 * TGM Chat - Android Client
 *
 * SPDX-FileCopyrightText: 2026 WAM
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.tgm

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** TGM: the app is for Vietnamese staff, so it is always shown in Vietnamese, whatever the phone language. */
object TgmLocale {
    private val VIETNAMESE: Locale = Locale.forLanguageTag("vi-VN")

    fun wrap(base: Context): Context {
        Locale.setDefault(VIETNAMESE)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(VIETNAMESE))
        return base.createConfigurationContext(config)
    }
}
