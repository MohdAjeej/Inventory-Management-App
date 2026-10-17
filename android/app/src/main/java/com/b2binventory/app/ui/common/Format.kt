package com.b2binventory.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.b2binventory.app.ui.theme.StatusColors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Fmt {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun qty(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    fun signedQty(value: Double): String = (if (value > 0) "+" else "") + qty(value)

    fun dateTime(iso: String?): String = iso?.let {
        runCatching { dateTime.format(Instant.parse(it).atZone(ZoneId.systemDefault())) }.getOrDefault(it)
    } ?: ""

    fun date(iso: String?): String = iso?.let {
        runCatching { date.format(LocalDate.parse(it.take(10))) }.getOrDefault(it)
    } ?: ""

    /** Amount in the business currency, e.g. "₹1,250.50". */
    fun money(value: Double, currencyCode: String): String = runCatching {
        NumberFormat.getCurrencyInstance(latinDigits()).apply {
            currency = Currency.getInstance(currencyCode)
            maximumFractionDigits = 2
        }.format(value)
    }.getOrDefault("$currencyCode ${String.format(java.util.Locale.ROOT, "%.2f", value)}")

    /** The current language, but always with 0-9 digits so numbers read and parse the same everywhere. */
    private fun latinDigits(): java.util.Locale =
        java.util.Locale.Builder().setLocale(java.util.Locale.getDefault()).setUnicodeLocaleKeyword("nu", "latn").build()

    /** Short amount for chart labels, e.g. "12.5K". */
    fun compact(value: Double): String = when {
        kotlin.math.abs(value) >= 10_000_000 -> qty(round1(value / 1_000_000)) + "M"
        kotlin.math.abs(value) >= 1_000 -> qty(round1(value / 1_000)) + "K"
        else -> qty(round1(value))
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    /** "+12.5%" / "-3%"; null growth means there is no earlier data to compare with. */
    fun growth(percent: Double?): String = when {
        percent == null -> "Not enough data to compare"
        percent > 0 -> "+${qty(percent)}% vs previous period"
        else -> "${qty(percent)}% vs previous period"
    }

    fun shortDate(iso: String): String = runCatching {
        LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM"))
    }.getOrDefault(iso)

    fun role(role: String): String = when (role) {
        "ADMIN" -> "Business Admin"
        "INVENTORY_MANAGER" -> "Inventory Manager"
        "STAFF" -> "Staff"
        else -> role
    }

    fun status(status: String): String = when (status) {
        "IN_STOCK" -> "In stock"
        "LOW_STOCK" -> "Low stock"
        "OUT_OF_STOCK" -> "Out of stock"
        else -> status
    }

    fun movement(type: String): String = when (type) {
        "OPENING" -> "Opening stock"
        "ADD" -> "Stock added"
        "REDUCE" -> "Stock reduced"
        "ADJUST" -> "Adjusted"
        "TRANSFER_OUT" -> "Transferred out"
        "TRANSFER_IN" -> "Transferred in"
        "COUNT_ADJUST" -> "Count correction"
        "DAMAGE" -> "Marked damaged"
        "RESERVE" -> "Reserved"
        "RELEASE" -> "Reservation released"
        "SALE" -> "Sold"
        "SALE_CANCEL" -> "Sale cancelled"
        "RETURN_IN" -> "Customer return"
        "PURCHASE" -> "Purchased"
        "PURCHASE_CANCEL" -> "Purchase cancelled"
        "PURCHASE_RETURN" -> "Returned to supplier"
        else -> type
    }

    fun fieldType(type: String): String = when (type) {
        "TEXT" -> "Text"
        "NUMBER" -> "Number"
        "DATE" -> "Date"
        "BOOLEAN" -> "Yes / No"
        "DROPDOWN" -> "Dropdown"
        "MULTI_SELECT" -> "Multi-select"
        else -> type
    }

    /** Human-readable value of a stored attribute. */
    fun attribute(value: JsonElement): String = when (value) {
        is JsonNull -> ""
        is JsonArray -> value.joinToString(", ") { attribute(it) }
        is JsonPrimitive -> value.booleanOrNull?.let { if (it) "Yes" else "No" } ?: value.content
        else -> value.toString()
    }
}

@Composable
fun statusColor(status: String): Color = when (status) {
    "OUT_OF_STOCK" -> StatusColors.out
    "LOW_STOCK" -> StatusColors.low
    else -> StatusColors.ok
}

@Composable
fun movementColor(quantity: Double): Color = when {
    quantity > 0 -> StatusColors.ok
    quantity < 0 -> StatusColors.out
    else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
}

fun String?.blankToNull(): String? = if (this.isNullOrBlank()) null else this.trim()
