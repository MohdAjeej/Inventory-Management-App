package com.b2binventory.app.ui.products

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.b2binventory.app.data.remote.AttributeDef
import com.b2binventory.app.ui.common.*
import kotlinx.serialization.json.*

/** Editable value of one custom field. */
sealed interface AttrValue {
    data class Text(val value: String) : AttrValue
    data class Flag(val value: Boolean?) : AttrValue
    data class Many(val values: Set<String>) : AttrValue
}

object Attributes {
    fun fromJson(def: AttributeDef, element: JsonElement?): AttrValue = when (def.fieldType) {
        "BOOLEAN" -> AttrValue.Flag((element as? JsonPrimitive)?.booleanOrNull)
        "MULTI_SELECT" -> AttrValue.Many((element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet() ?: emptySet())
        else -> AttrValue.Text((element as? JsonPrimitive)?.contentOrNull ?: "")
    }

    fun toJson(defs: List<AttributeDef>, values: Map<String, AttrValue>): Map<String, JsonElement> = buildMap {
        defs.forEach { def ->
            when (val v = values[def.key]) {
                is AttrValue.Text -> if (v.value.isNotBlank()) put(def.key, JsonPrimitive(v.value.trim()))
                is AttrValue.Flag -> v.value?.let { put(def.key, JsonPrimitive(it)) }
                is AttrValue.Many -> if (v.values.isNotEmpty()) put(def.key, JsonArray(v.values.map { JsonPrimitive(it) }))
                null -> Unit
            }
        }
    }

    /** Client-side check mirroring the server's validation (the server remains authoritative). */
    fun validate(defs: List<AttributeDef>, values: Map<String, AttrValue>): Map<String, String> = buildMap {
        defs.forEach { def ->
            val v = values[def.key]
            val empty = when (v) {
                is AttrValue.Text -> v.value.isBlank()
                is AttrValue.Flag -> v.value == null
                is AttrValue.Many -> v.values.isEmpty()
                null -> true
            }
            if (def.required && empty) put("attributes.${def.key}", "${def.label} is required")
            if (def.fieldType == "NUMBER" && v is AttrValue.Text && v.value.isNotBlank() && v.value.trim().toBigDecimalOrNull() == null) {
                put("attributes.${def.key}", "${def.label} must be a number")
            }
        }
    }
}

@Composable
fun AttributeInput(def: AttributeDef, value: AttrValue?, error: String?, onChange: (AttrValue) -> Unit) {
    when (def.fieldType) {
        "NUMBER" -> FormField((value as? AttrValue.Text)?.value ?: "", { onChange(AttrValue.Text(it)) }, def.label,
            error = error, required = def.required, keyboardType = KeyboardType.Decimal)
        "DATE" -> DateField(def.label, (value as? AttrValue.Text)?.value ?: "", { onChange(AttrValue.Text(it)) },
            error = error, required = def.required)
        "BOOLEAN" -> Column {
            val checked = (value as? AttrValue.Flag)?.value ?: false
            SwitchRow(if (def.required) "${def.label} *" else def.label, checked, { onChange(AttrValue.Flag(it)) })
            if (error != null) androidx.compose.material3.Text(error, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
        "DROPDOWN" -> DropdownField(def.label, def.options, (value as? AttrValue.Text)?.value?.takeIf { it.isNotBlank() },
            { onChange(AttrValue.Text(it)) }, { it }, error = error, required = def.required,
            noneLabel = if (def.required) null else "Not set", onNone = { onChange(AttrValue.Text("")) })
        "MULTI_SELECT" -> {
            val selected = (value as? AttrValue.Many)?.values ?: emptySet()
            MultiSelectChips(def.label, def.options, selected, { o ->
                onChange(AttrValue.Many(if (o in selected) selected - o else selected + o))
            }, error = error, required = def.required)
        }
        else -> FormField((value as? AttrValue.Text)?.value ?: "", { onChange(AttrValue.Text(it)) }, def.label,
            error = error, required = def.required)
    }
}
