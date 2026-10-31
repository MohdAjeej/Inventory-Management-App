package com.b2binventory.app.ui.catalog

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.b2binventory.app.data.remote.UnitCategoryDto
import com.b2binventory.app.data.remote.UnitDto
import com.b2binventory.app.data.remote.UnitRequest
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.theme.Spacing

/** Units matching a search (name or abbreviation) and a category, grouped by category in the server's order. */
fun groupUnits(units: List<UnitDto>, categories: List<UnitCategoryDto>, query: String,
               category: String?): List<Pair<UnitCategoryDto, List<UnitDto>>> {
    val q = query.trim().lowercase()
    val matching = units.filter {
        (category == null || it.category == category) &&
            (q.isEmpty() || it.name.lowercase().contains(q) || it.code.lowercase().contains(q))
    }
    val groups = categories.ifEmpty { matching.map { it.category }.distinct().map { UnitCategoryDto(it, it) } }
    return groups.mapNotNull { c -> matching.filter { it.category == c.code }.takeIf { it.isNotEmpty() }?.let { c to it } }
}

/** "1 DOZ = 12 PCS" for a unit with a fixed size, or null. */
fun unitEquivalence(u: UnitDto, categories: List<UnitCategoryDto>): String? {
    val base = categories.firstOrNull { it.code == u.category }?.baseUnitCode ?: return null
    val factor = u.baseFactor ?: return null
    return if (u.code.equals(base, ignoreCase = true)) null else "1 ${u.code} = ${Fmt.qty(factor)} $base"
}

/** Checks a unit form and returns the request to send, or the field errors to show. */
fun unitRequest(code: String, name: String, decimals: Int, category: String, baseFactor: String): Pair<UnitRequest?, Map<String, String>> {
    val factor = baseFactor.trim().takeIf { it.isNotEmpty() && category != "OTHER" }?.toDoubleOrNull()
    val errors = buildMap {
        if (code.isBlank()) put("code", "Abbreviation is required")
        if (name.isBlank()) put("name", "Name is required")
        if (category != "OTHER" && baseFactor.isNotBlank() && (factor == null || factor <= 0)) put("baseFactor", "Enter a positive number")
    }
    return if (errors.isNotEmpty()) null to errors
    else UnitRequest(code.trim(), name.trim(), decimals, category, factor) to emptyMap()
}

/** Search box and category chips for a unit list. */
@Composable
fun UnitFilters(
    query: String,
    onQuery: (String) -> Unit,
    categories: List<UnitCategoryDto>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SearchField(query, onQuery, tr("Search by name or abbreviation"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            item { FilterChip(selected == null, { onSelect(null) }, label = { Text("All") }) }
            items(categories, key = { it.code }) { c ->
                FilterChip(selected == c.code, { onSelect(if (selected == c.code) null else c.code) }, label = { Text(c.label) })
            }
        }
    }
}

/** Category headings, each followed by its units. */
fun LazyListScope.unitGroups(groups: List<Pair<UnitCategoryDto, List<UnitDto>>>, row: @Composable (UnitDto) -> Unit) {
    groups.forEach { (category, units) ->
        item(key = "group-${category.code}") {
            Text(category.label, Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.xs),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        items(units, key = { it.id }) { row(it) }
    }
}

/** One unit: name, abbreviation and fixed size; standard units carry a lock. */
@Composable
fun UnitRow(
    unit: UnitDto,
    categories: List<UnitCategoryDto>,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { androidx.compose.material3.Text(unit.name + if (unit.archived) " · ${tr("archived")}" else "") },
        supportingContent = {
            androidx.compose.material3.Text(listOfNotNull(unit.code, unitEquivalence(unit, categories),
                if (unit.standard) null else tr("Custom")).joinToString(" · "))
        },
        leadingContent = if (selected) ({ Icon(Icons.Default.Check, tr("Selected"), tint = MaterialTheme.colorScheme.primary) }) else null,
        trailingContent = trailing ?: if (unit.standard) ({
            Icon(Icons.Default.Lock, tr("Standard unit"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }) else null,
        modifier = modifier,
    )
}

/** A read-only field showing the chosen unit; tapping it opens a searchable list grouped by category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitPickerField(
    units: List<UnitDto>,
    categories: List<UnitCategoryDto>,
    selectedId: Long?,
    onSelected: (UnitDto) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Unit of measurement",
    error: String? = null,
    required: Boolean = false,
    onAddCustom: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = units.firstOrNull { it.id == selectedId }?.label ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(if (required) "${tr(label)} *" else label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
    if (open) {
        UnitPickerSheet(
            units.filter { !it.archived || it.id == selectedId }, categories, selectedId,
            onSelected = { open = false; onSelected(it) },
            onDismiss = { open = false },
            onAddCustom = onAddCustom?.let { add -> { open = false; add() } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitPickerSheet(
    units: List<UnitDto>,
    categories: List<UnitCategoryDto>,
    selectedId: Long?,
    onSelected: (UnitDto) -> Unit,
    onDismiss: () -> Unit,
    onAddCustom: (() -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(units, categories, query, category) { groupUnits(units, categories, query, category) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choose a unit", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (onAddCustom != null) {
                    TextButton(onClick = onAddCustom) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Custom unit")
                    }
                }
            }
            UnitFilters(query, { query = it }, categories, category, { category = it })
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            if (groups.isEmpty()) {
                item {
                    Text("No units match your search.", Modifier.padding(Spacing.l),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            unitGroups(groups) { u ->
                UnitRow(u, categories, Modifier.clickable { onSelected(u) }, selected = u.id == selectedId)
            }
        }
    }
}

/** Add or edit a custom unit: abbreviation, name, decimals, what it measures and, optionally, its fixed size. */
@Composable
fun UnitDialog(
    unit: UnitDto?,
    categories: List<UnitCategoryDto>,
    errors: Map<String, String>,
    onSave: (code: String, name: String, decimals: Int, category: String, baseFactor: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf(unit?.code ?: "") }
    var name by remember { mutableStateOf(unit?.name ?: "") }
    var decimals by remember { mutableIntStateOf(unit?.decimals ?: 0) }
    var category by remember { mutableStateOf(unit?.category ?: "OTHER") }
    var baseFactor by remember { mutableStateOf(unit?.baseFactor?.let(Fmt::qty) ?: "") }
    val base = categories.firstOrNull { it.code == category }?.baseUnitCode
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (unit == null) "New custom unit" else "Edit unit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it }, "Name", required = true, error = errors["name"], placeholder = "e.g. Tola")
                FormField(code, { code = it }, "Abbreviation", required = true, error = errors["code"], placeholder = "e.g. TOLA")
                DropdownField("Measures", categories, categories.firstOrNull { it.code == category }, { category = it.code },
                    { it.label }, error = errors["category"])
                if (base != null) {
                    FormField(baseFactor, { baseFactor = it }, "1 ${code.ifBlank { tr("unit") }} = how many $base?",
                        keyboardType = KeyboardType.Decimal, error = errors["baseFactor"],
                        supporting = "Optional. Leave empty if its size varies (a box, a bag). Filled in, it converts automatically.")
                }
                DropdownField("Decimal places", (0..4).toList(), decimals, { decimals = it },
                    { if (it == 0) "0 (whole numbers)" else it.toString() })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(code, name, decimals, category, baseFactor) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
