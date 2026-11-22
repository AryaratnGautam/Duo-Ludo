package com.easyprint.shop.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easyprint.shop.printing.ColorMode
import com.easyprint.shop.printing.PaperSize
import com.easyprint.shop.printing.PrintDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.easyPrintDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "easyprint_settings"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_PAPER_SIZE = stringPreferencesKey("default_paper_size")
        val DEFAULT_COLOR_MODE = stringPreferencesKey("default_color_mode")
    }

    val defaultsFlow: Flow<PrintDefaults> = context.easyPrintDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val paper = prefs[Keys.DEFAULT_PAPER_SIZE]
                ?.let { runCatching { PaperSize.valueOf(it) }.getOrNull() }
                ?: PaperSize.A4
            val color = prefs[Keys.DEFAULT_COLOR_MODE]
                ?.let { runCatching { ColorMode.valueOf(it) }.getOrNull() }
                ?: ColorMode.COLOR
            PrintDefaults(paperSize = paper, colorMode = color)
        }

    suspend fun updateDefaultPaperSize(paperSize: PaperSize) {
        context.easyPrintDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PAPER_SIZE] = paperSize.name
        }
    }

    suspend fun updateDefaultColorMode(colorMode: ColorMode) {
        context.easyPrintDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_COLOR_MODE] = colorMode.name
        }
    }
}
