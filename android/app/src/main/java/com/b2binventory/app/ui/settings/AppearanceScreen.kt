package com.b2binventory.app.ui.settings

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.b2binventory.app.R
import com.b2binventory.app.data.prefs.AppPreferences
import com.b2binventory.app.data.prefs.ThemeMode
import com.b2binventory.app.ui.common.AppTopBar
import com.b2binventory.app.ui.common.SectionCard
import com.b2binventory.app.ui.common.appContainer
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val prefs = appContainer().appPreferences
    val mode by prefs.themeMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Changing the language recreates the activity, so reading it once per composition is enough.
    val language = remember { prefs.languageTag }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.appearance_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(stringResource(R.string.appearance_theme)) {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        ThemeMode.SYSTEM to R.string.appearance_theme_system,
                        ThemeMode.LIGHT to R.string.appearance_theme_light,
                        ThemeMode.DARK to R.string.appearance_theme_dark,
                    ).forEach { (option, label) ->
                        ChoiceRow(stringResource(label), selected = mode == option) {
                            scope.launch { prefs.setThemeMode(option) }
                        }
                    }
                }
            }
            SectionCard(stringResource(R.string.appearance_language)) {
                Column(Modifier.selectableGroup()) {
                    ChoiceRow(stringResource(R.string.appearance_language_system), selected = language.isEmpty()) {
                        prefs.setLanguage("")
                    }
                    AppPreferences.languages.forEach { lang ->
                        ChoiceRow(lang.nativeName, selected = language == lang.tag) { prefs.setLanguage(lang.tag) }
                    }
                }
                Text(
                    stringResource(R.string.appearance_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
