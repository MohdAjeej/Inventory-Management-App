package com.b2binventory.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.b2binventory.app.ui.theme.MetricTextStyle
import com.b2binventory.app.ui.theme.Spacing
import com.b2binventory.app.ui.theme.appColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------------------------------------------------------- bars and states

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back")) }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** An icon in a soft tinted circle; used by empty states, metric cards and list rows. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.5f), tint = tint) }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(Icons.Default.CloudOff, MaterialTheme.colorScheme.error, 72.dp)
        Spacer(Modifier.height(Spacing.l))
        Text("Could not load", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.l))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Try again")
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Default.Inventory2,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, 72.dp)
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.l))
            action()
        }
    }
}

/** Renders loading / error / content for a [Loadable]. */
@Composable
fun <T> LoadableContent(state: Loadable<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    val data = state.data
    when {
        data != null -> content(data)
        state.loading -> LoadingBox()
        else -> ErrorState(state.error ?: "Could not load", onRetry)
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(title, confirmLabel, destructive, onConfirm, onDismiss,
        icon = if (destructive) Icons.Default.WarningAmber else null) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Confirmation with structured content, e.g. [InfoRow]s and a [StockChangePreview]. */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        title = { Text(title, textAlign = if (icon != null) TextAlign.Center else null) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s),
                content = content)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- form fields

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    required: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    placeholder: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "${tr(label)} *" else label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = tr(if (visible) "Hide password" else "Show password"))
                }
            }
        } else null,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** The shared search bar used above lists. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
                isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, tr("Clear")) }
        },
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Read-only dropdown built on ExposedDropdownMenuBox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    error: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    noneLabel: String? = null,
    onNone: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel)?.let(::tr) ?: (noneLabel?.let(::tr) ?: ""),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (required) "${tr(label)} *" else label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null && onNone != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onNone(); expanded = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

/** Text field that opens a Material date picker; value is an ISO date (yyyy-MM-dd) or empty. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    required: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else Fmt.date(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(if (required) "${tr(label)} *" else label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = {
                Row {
                    if (value.isNotBlank()) {
                        IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, tr("Clear date")) }
                    }
                    IconButton(onClick = { open = true }) { Icon(Icons.Default.CalendarMonth, tr("Pick date")) }
                }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (open) {
        val initial = value.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectChips(
    label: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    error: String? = null,
    required: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(if (required) "${tr(label)} *" else label, style = MaterialTheme.typography.labelLarge,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            options.forEach { option ->
                FilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(option) })
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Single choice shown as chips, e.g. reasons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    label: String?,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    error: String? = null,
    required: Boolean = false,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (label != null) {
            Text(if (required) "${tr(label)} *" else label, style = MaterialTheme.typography.labelLarge,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) },
                    leadingIcon = if (option == selected) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null,
                )
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null,
              enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ---------------------------------------------------------------- cards and sections

/** Flat card with a hairline border, the standard container for grouped content. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors,
            border = border, content = content)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border,
            content = content)
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (title != null) SectionHeader(title, actionLabel, onAction, Modifier)
            content()
        }
    }
}

/** Title for a group of content with an optional "See all"-style action. */
@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null,
                  modifier: Modifier = Modifier.padding(top = Spacing.s)) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = Spacing.s)) {
                Text(actionLabel)
                Spacer(Modifier.width(Spacing.xs))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

/** Pill-shaped label used for statuses and tags. */
@Composable
fun Pill(text: String, color: Color, icon: ImageVector? = null) {
    Surface(color = color.copy(alpha = 0.14f), contentColor = color, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

fun stockStatusIcon(status: String): ImageVector = when (status) {
    "OUT_OF_STOCK" -> Icons.Default.RemoveCircleOutline
    "LOW_STOCK" -> Icons.Default.WarningAmber
    else -> Icons.Default.CheckCircleOutline
}

/** Stock status shown with an icon and text as well as colour. */
@Composable
fun StatusChip(status: String) {
    Pill(Fmt.status(status), statusColor(status), stockStatusIcon(status))
}

/** Small tag such as "Top seller". */
@Composable
fun TagChip(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Pill(text, color)
}

/** One labelled quick action used on Home and Stock. */
@Composable
fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
               tint: Color = MaterialTheme.colorScheme.primary) {
    AppCard(modifier.heightIn(min = 96.dp), onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(icon, tint, 40.dp)
            Spacer(Modifier.height(Spacing.s))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A label and its value on one line, used in summaries and detail screens. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, emphasis: Boolean = false,
            valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.45f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(Spacing.s))
        Text(value, Modifier.weight(0.55f), style = if (emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End, color = valueColor)
    }
}

@Composable
fun LabelValue(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return
    InfoRow(label, value, modifier)
}

enum class BannerKind { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun MessageBanner(kind: BannerKind, message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    val c = MaterialTheme.appColors
    val (bg, fg, icon) = when (kind) {
        BannerKind.INFO -> Triple(c.infoContainer, c.onInfoContainer, Icons.Default.Info)
        BannerKind.SUCCESS -> Triple(c.successContainer, c.onSuccessContainer, Icons.Default.CheckCircle)
        BannerKind.WARNING -> Triple(c.warningContainer, c.onWarningContainer, Icons.Default.WarningAmber)
        BannerKind.ERROR -> Triple(c.dangerContainer, c.onDangerContainer, Icons.Default.ErrorOutline)
    }
    Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg)
            Spacer(Modifier.width(Spacing.s))
            Text(message, color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ErrorBanner(message: String?) = MessageBanner(BannerKind.ERROR, message)

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, loading: Boolean = false, enabled: Boolean = true,
                  modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.s))
            }
            Text(text)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                    icon: ImageVector? = null) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text)
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                 icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text)
    }
}

/** Scrollable form column with standard padding, centred on wide screens. */
@Composable
fun FormColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content,
        )
    }
}

// ---------------------------------------------------------------- metrics

/** Metric card: tinted icon, large value and a label; the whole card is clickable when [onClick] is set. */
@Composable
fun StatTile(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier,
             supporting: String? = null, onClick: (() -> Unit)? = null, compact: Boolean = false) {
    if (compact) {
        CompactStatTile(label, value, icon, color, modifier, supporting, onClick)
        return
    }
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, color, 36.dp)
                Spacer(Modifier.weight(1f))
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(Spacing.m))
            Text(value, style = MetricTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
    }
}

/** Dense variant of [StatTile] for dashboards: icon beside the figure, one-line label. */
@Composable
private fun CompactStatTile(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier,
                            supporting: String?, onClick: (() -> Unit)?) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, color, 28.dp)
                Spacer(Modifier.width(Spacing.s))
                androidx.compose.material3.Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Initials in a tinted circle, for people and businesses. */
@Composable
fun Avatar(name: String, size: Dp = 40.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(initials, color = color, style = if (size >= 48.dp) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Colour for a role badge. */
@Composable
fun roleColor(role: String): Color = when (role) {
    "ADMIN" -> MaterialTheme.colorScheme.primary
    "INVENTORY_MANAGER" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun RoleBadge(role: String) = Pill(Fmt.role(role), roleColor(role), Icons.Default.Badge)

/**
 * Shows what a stock change will do: current quantity → quantity after, coloured by direction,
 * with an optional warning (e.g. not enough stock).
 */
@Composable
fun StockChangePreview(current: Double, after: Double, unit: String, warning: String? = null,
                       currentLabel: String = "Current stock", afterLabel: String = "After this change") {
    val c = MaterialTheme.appColors
    val tone = when {
        warning != null -> c.danger
        after > current -> c.success
        after < current -> c.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = tone.copy(alpha = 0.08f), shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(currentLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Fmt.qty(current)} $unit", style = MaterialTheme.typography.titleLarge)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = tone)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(afterLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Fmt.qty(after)} $unit", style = MaterialTheme.typography.titleLarge, color = tone)
                }
            }
            val diff = after - current
            if (warning != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, Modifier.size(18.dp), tint = c.danger)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(warning, color = c.danger, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            } else if (diff != 0.0) {
                Text("${Fmt.signedQty(diff)} $unit", color = tone, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
