package com.b2binventory.app.ui.products

import com.b2binventory.app.ui.common.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import com.b2binventory.app.ui.dashboard.MovementRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

class ProductDetailViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    val id: Long = checkNotNull(saved["id"])
    private val _product = MutableStateFlow(Loadable<ProductDetail>())
    val product = _product.asStateFlow()
    private val _history = MutableStateFlow<List<Movement>>(emptyList())
    val history = _history.asStateFlow()
    private val _labels = MutableStateFlow<Map<String, String>>(emptyMap())
    val labels = _labels.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _variantErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val variantErrors = _variantErrors.asStateFlow()

    fun load() {
        _product.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = apiCall { api.product(id) }) {
                is ApiResult.Ok -> {
                    _product.value = Loadable(r.value, false)
                    (apiCall { api.formSchema(r.value.categoryId) } as? ApiResult.Ok)?.let { s ->
                        _labels.value = s.value.fields.associate { it.key to it.label }
                    }
                }
                is ApiResult.Err -> _product.update { it.copy(loading = false, error = r.message) }
            }
            (apiCall { api.movements(productId = id, size = 20) } as? ApiResult.Ok)?.let { _history.value = it.value.content }
        }
    }

    private fun mutate(message: String, onSuccess: () -> Unit = {}, block: suspend () -> ProductDetail) {
        viewModelScope.launch {
            when (val r = apiCall(block)) {
                is ApiResult.Ok -> {
                    _product.value = Loadable(r.value, false)
                    _variantErrors.value = emptyMap()
                    onSuccess()
                    _messages.emit(message)
                }
                is ApiResult.Err -> {
                    if (r.fieldErrors.isNotEmpty()) _variantErrors.value = r.fieldErrors
                    _messages.emit(r.message)
                }
            }
        }
    }

    fun archive() = mutate("Product archived") { api.archiveProduct(id) }
    fun restore() = mutate("Product restored") { api.restoreProduct(id) }

    fun saveVariant(variantId: Long?, request: VariantRequest, onSuccess: () -> Unit) {
        if (request.name.isBlank()) {
            _variantErrors.value = mapOf("name" to "Name is required")
            return
        }
        mutate(if (variantId == null) "Variant added" else "Variant updated", onSuccess) {
            if (variantId == null) api.addVariant(id, request) else api.updateVariant(variantId, request)
        }
    }

    fun archiveVariant(variantId: Long) = mutate("Variant archived") { api.archiveVariant(variantId) }
    fun restoreVariant(variantId: Long) = mutate("Variant restored") { api.restoreVariant(variantId) }

    fun uploadImage(context: android.content.Context, uri: android.net.Uri) {
        val part = ImageParts.fromUri(context, uri)
        if (part == null) {
            viewModelScope.launch { _messages.emit("Image is unreadable or larger than 5 MB") }
            return
        }
        mutate("Image added") { api.uploadImage(id, part) }
    }

    fun deleteImage(imageId: Long) = mutate("Image removed") { api.deleteImage(id, imageId) }

    fun clearVariantErrors() {
        _variantErrors.value = emptyMap()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onStockOperation: (Long, String) -> Unit) {
    val vm = appViewModel { c, s -> ProductDetailViewModel(c.api, s) }
    val state by vm.product.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val variantErrors by vm.variantErrors.collectAsStateWithLifecycle()
    val me = LocalMe.current
    val container = appContainer()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmArchive by remember { mutableStateOf(false) }
    var editingVariant by remember { mutableStateOf<Variant?>(null) }
    var addingVariant by remember { mutableStateOf(false) }
    var deleteImage by remember { mutableStateOf<ImageDto?>(null) }
    var menu by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(tr(it)) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.uploadImage(context, uri)
    }

    Scaffold(
        topBar = {
            AppTopBar(state.data?.name ?: "Product", onBack = onBack) {
                val p = state.data
                if (p != null && me?.can("PRODUCT_EDIT") == true && !p.archived) {
                    IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Default.Edit, "Edit") }
                }
                if (p != null && me?.can("PRODUCT_ARCHIVE") == true) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        if (p.archived) DropdownMenuItem({ Text("Restore product") }, { menu = false; vm.restore() })
                        else DropdownMenuItem({ Text("Archive product") }, { menu = false; confirmArchive = true })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LoadableContent(state, vm::load) { p ->
            FormColumn(padding) {
                if (p.archived) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                        Text("This product is archived. Restore it to change its stock.", Modifier.padding(12.dp))
                    }
                }
                if (p.images.isNotEmpty() || me?.can("PRODUCT_EDIT") == true) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(p.images, key = { it.id }) { img ->
                            AsyncImage(container.fileUrl(img.url), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(
                                    onClick = {}, onLongClick = { if (me?.can("PRODUCT_EDIT") == true) deleteImage = img }))
                        }
                        if (me?.can("PRODUCT_EDIT") == true && p.images.size < 10) {
                            item {
                                OutlinedCard(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.size(120.dp)) {
                                    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.AddAPhoto, null)
                                        Text("Add photo", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            listOfNotNull(p.brand, p.model).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        StatusChip(p.status)
                    }
                    LabelValue("Category", p.categoryPath ?: "Uncategorized")
                    LabelValue("SKU", p.defaultVariant?.sku)
                    LabelValue("Barcode", p.defaultVariant?.barcode)
                    LabelValue("Unit", "${p.baseUnit.name} (${p.baseUnit.code})")
                    LabelValue("Minimum stock", p.effectiveMinStock.toString() + if (p.minStock == null) " (business default)" else "")
                    LabelValue("Supplier", p.supplierName)
                    p.taxRate?.let { LabelValue("Tax rate", "${Fmt.qty(it)}%") }
                    if (p.trackBatches) LabelValue("Tracking", "Batches / lots")
                    if (p.trackSerials) LabelValue("Tracking", "Serial numbers")
                    p.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                SectionCard("Stock") {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        StockFigure("Available", p.stock.available, p.baseUnit.code)
                        StockFigure("On hand", p.stock.onHand, p.baseUnit.code)
                        if (p.stock.reserved != 0.0) StockFigure("Reserved", p.stock.reserved, p.baseUnit.code)
                        if (p.stock.damaged != 0.0) StockFigure("Damaged", p.stock.damaged, p.baseUnit.code)
                    }
                    if (p.balances.isNotEmpty()) {
                        HorizontalDivider()
                        p.balances.forEach { b ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.warehouseName)
                                    val extra = listOfNotNull(
                                        b.variantName.takeIf { p.variants.size > 1 },
                                        b.lotNumber?.let { "Lot $it" },
                                        b.expiryDate?.let { "exp ${Fmt.date(it)}" },
                                    ).joinToString(" · ")
                                    if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${Fmt.qty(b.available)} ${p.baseUnit.code}")
                            }
                        }
                    }
                    if (!p.archived) StockActions(p.id, onStockOperation)
                }
                PricingCard(p)
                if (p.attributes.isNotEmpty()) {
                    SectionCard("Details") {
                        p.attributes.forEach { (key, value) -> LabelValue(labels[key] ?: key, Fmt.attribute(value)) }
                    }
                }
                SectionCard("Variants") {
                    Text("Sizes, colours or other versions of this product. Stock is kept per variant.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    p.variants.forEach { v ->
                        ListItem(
                            headlineContent = { Text(v.name + if (v.isDefault) " (main)" else "") },
                            supportingContent = {
                                Text(listOfNotNull(v.sku, v.barcode, v.attributes.entries.joinToString { "${it.key}: ${Fmt.attribute(it.value)}" }
                                    .takeIf { it.isNotEmpty() }, "archived".takeIf { v.archived }).joinToString(" · ").ifEmpty { "—" })
                            },
                            trailingContent = { Text("${Fmt.qty(v.stock.available)} ${p.baseUnit.code}") },
                            modifier = Modifier.combinedClickable(onClick = {
                                if (me?.can("PRODUCT_EDIT") == true) editingVariant = v
                            }),
                        )
                    }
                    if (me?.can("PRODUCT_EDIT") == true && !p.archived) {
                        TextButton(onClick = { addingVariant = true }) {
                            Icon(Icons.Default.Add, null)
                            Text("Add variant")
                        }
                    }
                }
                SectionCard("Recent history") {
                    if (history.isEmpty()) Text("No stock movements yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    history.forEach { MovementRow(it) }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (addingVariant || editingVariant != null) {
                VariantDialog(editingVariant, variantErrors,
                    canArchive = me?.can("PRODUCT_ARCHIVE") == true,
                    onSave = { req -> vm.saveVariant(editingVariant?.id, req) { addingVariant = false; editingVariant = null } },
                    onArchive = { v -> if (v.archived) vm.restoreVariant(v.id) else vm.archiveVariant(v.id); editingVariant = null },
                    onDismiss = { addingVariant = false; editingVariant = null; vm.clearVariantErrors() })
            }
        }
    }
    if (confirmArchive) {
        ConfirmDialog("Archive product?", "It will be hidden from lists and its stock can no longer change. History is kept and you can restore it later.",
            "Archive", true, onConfirm = { confirmArchive = false; vm.archive() }, onDismiss = { confirmArchive = false })
    }
    deleteImage?.let { img ->
        ConfirmDialog("Remove photo?", "The image will be deleted.", "Remove", true,
            onConfirm = { vm.deleteImage(img.id); deleteImage = null }, onDismiss = { deleteImage = null })
    }
}

/** Purchase and selling price with the profit they imply. Costs are only present for members allowed to see them. */
@Composable
private fun PricingCard(p: ProductDetail) {
    val container = appContainer()
    var currency by remember { mutableStateOf("INR") }
    LaunchedEffect(Unit) { (apiCall { container.api.settings() } as? ApiResult.Ok)?.let { currency = it.value.currencyCode } }
    val unit = p.baseUnit.code
    val pr = p.pricing
    SectionCard("Prices") {
        if (pr != null) LabelValue("Purchase price", p.purchasePrice?.let { "${Fmt.money(it, currency)} / $unit" } ?: "Not set")
        LabelValue("Selling price", p.salePrice?.let { "${Fmt.money(it, currency)} / $unit" } ?: "Not set")
        if (pr != null) {
            pr.unitCost?.let {
                LabelValue("Cost used for profit", "${Fmt.money(it, currency)} / $unit" +
                    if (pr.costSource == "AVERAGE") " (average of purchases)" else " (purchase price)")
            }
            if (pr.profitPerUnit != null) {
                HorizontalDivider()
                LabelValue("Gross profit per $unit", Fmt.money(pr.profitPerUnit, currency) + (pr.marginPercent?.let { " (${Fmt.qty(it)}% margin)" } ?: ""))
                LabelValue("Stock value at cost", pr.stockValueAtCost?.let { Fmt.money(it, currency) })
                LabelValue("Stock value at selling price", pr.stockValueAtSellingPrice?.let { Fmt.money(it, currency) })
                LabelValue("Potential profit on ${Fmt.qty(p.stock.available)} $unit", pr.potentialProfit?.let { Fmt.money(it, currency) })
                Text("Assumes all available stock sells at the selling price, before other costs.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Add both a purchase price and a selling price to see the profit.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StockFigure(label: String, value: Double, unit: String) {
    Column {
        Text("${Fmt.qty(value)} $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockActions(productId: Long, onStockOperation: (Long, String) -> Unit) {
    val me = LocalMe.current ?: return
    // "SALE" opens the Record sale screen; the others open the stock operation screen.
    val actions = buildList {
        if (me.can("STOCK_ADD")) add("ADD" to "Add Stock")
        if (me.can("STOCK_REDUCE")) add("REDUCE" to "Reduce Stock")
        if (me.can("SALE_RECORD")) add("SALE" to "Record Sale")
        if (me.can("PURCHASE_MANAGE")) add("PURCHASE" to "Record Purchase")
        if (me.can("STOCK_ADJUST")) add("ADJUST" to "Correct stock")
        if (me.can("STOCK_TRANSFER")) add("TRANSFER" to "Move")
    }
    if (actions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (type, label) ->
            FilledTonalButton(onClick = { onStockOperation(productId, type) }) { Text(label) }
        }
    }
}

@Composable
private fun VariantDialog(
    variant: Variant?,
    errors: Map<String, String>,
    canArchive: Boolean,
    onSave: (VariantRequest) -> Unit,
    onArchive: (Variant) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(variant?.name ?: "") }
    var sku by remember { mutableStateOf(variant?.sku ?: "") }
    var barcode by remember { mutableStateOf(variant?.barcode ?: "") }
    var minStock by remember { mutableStateOf(variant?.minStock?.toString() ?: "") }
    var attrs by remember {
        mutableStateOf(variant?.attributes?.entries?.joinToString("\n") { "${it.key}: ${Fmt.attribute(it.value)}" } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (variant == null) "Add variant" else "Edit variant") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it }, "Variant name", required = true, error = errors["name"], placeholder = "e.g. Large / Red")
                FormField(sku, { sku = it }, "SKU", error = errors["sku"])
                FormField(barcode, { barcode = it }, "Barcode", error = errors["barcode"])
                FormField(minStock, { minStock = it.filter(Char::isDigit) }, "Minimum stock", keyboardType = KeyboardType.Number,
                    supporting = "Leave empty to use the product's minimum")
                FormField(attrs, { attrs = it }, "Attributes", singleLine = false, minLines = 2,
                    placeholder = "size: L\ncolour: Red", error = errors["attributes"])
                if (variant != null && !variant.isDefault && canArchive) {
                    TextButton(onClick = { onArchive(variant) }) { Text(if (variant.archived) "Restore variant" else "Archive variant") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val attributes = attrs.lines().mapNotNull { line ->
                    val idx = line.indexOf(':')
                    if (idx <= 0) null else line.substring(0, idx).trim() to JsonPrimitive(line.substring(idx + 1).trim())
                }.filter { it.first.isNotEmpty() }.toMap()
                onSave(VariantRequest(name.trim(), sku.blankToNull(), barcode.blankToNull(), attributes, minStock.toIntOrNull()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
