package com.b2binventory.app.ui.products

import com.b2binventory.app.ui.common.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.b2binventory.app.data.remote.*
import com.b2binventory.app.ui.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProductForm(
    val name: String = "",
    val categoryId: Long? = null,
    val unitId: Long? = null,
    val brand: String = "",
    val model: String = "",
    val sku: String = "",
    val barcode: String = "",
    val description: String = "",
    val minStock: String = "",
    val salePrice: String = "",
    val purchasePrice: String = "",
    val taxRate: String = "",
    val supplierId: Long? = null,
    val trackBatches: Boolean = false,
    val trackSerials: Boolean = false,
    val openingQty: String = "",
    val warehouseId: Long? = null,
    val lotNumber: String = "",
    val expiryDate: String = "",
    val serials: String = "",
    val attributes: Map<String, AttrValue> = emptyMap(),
)

data class ProductFormState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val savedId: Long? = null,
)

class ProductFormViewModel(private val api: ApiService, saved: SavedStateHandle) : ViewModel() {
    val editId: Long? = saved.get<Long>("id")
    private val _form = MutableStateFlow(ProductForm())
    val form = _form.asStateFlow()
    private val _state = MutableStateFlow(ProductFormState())
    val state = _state.asStateFlow()
    private val _schema = MutableStateFlow(FormSchema())
    val schema = _schema.asStateFlow()
    private val _categories = MutableStateFlow<List<CategoryNode>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _units = MutableStateFlow<List<UnitDto>>(emptyList())
    val units = _units.asStateFlow()
    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ProductFormState(loading = true)
        viewModelScope.launch {
            val result = apiCall {
                val cats = api.categories().flatten()
                val units = api.units()
                val warehouses = api.warehouses()
                val product = editId?.let { api.product(it) }
                val schema = api.formSchema(product?.categoryId)
                _categories.value = cats
                _units.value = units
                _warehouses.value = warehouses
                _schema.value = schema
                _form.value = if (product == null) {
                    ProductForm(
                        unitId = units.firstOrNull { it.code == "pcs" }?.id ?: units.firstOrNull()?.id,
                        warehouseId = warehouses.firstOrNull { it.isDefault }?.id ?: warehouses.firstOrNull()?.id,
                    )
                } else {
                    ProductForm(
                        name = product.name, categoryId = product.categoryId, unitId = product.baseUnit.id,
                        brand = product.brand ?: "", model = product.model ?: "", sku = product.defaultVariant?.sku ?: "",
                        barcode = product.defaultVariant?.barcode ?: "", description = product.description ?: "",
                        minStock = product.minStock?.toString() ?: "", trackBatches = product.trackBatches,
                        salePrice = product.salePrice?.let { Fmt.qty(it) } ?: "",
                        purchasePrice = product.purchasePrice?.let { Fmt.qty(it) } ?: "",
                        taxRate = product.taxRate?.let { Fmt.qty(it) } ?: "",
                        supplierId = product.supplierId,
                        trackSerials = product.trackSerials,
                        attributes = schema.fields.associate { it.key to Attributes.fromJson(it, product.attributes[it.key]) },
                    )
                }
            }
            _state.value = when (result) {
                is ApiResult.Ok -> ProductFormState(loading = false)
                is ApiResult.Err -> ProductFormState(loading = false, loadError = result.message)
            }
        }
    }

    fun update(transform: (ProductForm) -> ProductForm) = _form.update(transform)

    fun setCategory(categoryId: Long?) {
        _form.update { it.copy(categoryId = categoryId) }
        viewModelScope.launch {
            when (val r = apiCall { api.formSchema(categoryId) }) {
                is ApiResult.Ok -> {
                    _schema.value = r.value
                    // Keep values for fields that still apply.
                    _form.update { f -> f.copy(attributes = f.attributes.filterKeys { k -> r.value.fields.any { it.key == k } }) }
                }
                is ApiResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun setAttribute(key: String, value: AttrValue) = _form.update { it.copy(attributes = it.attributes + (key to value)) }

    fun save() {
        val f = _form.value
        val schema = _schema.value
        val errors = buildMap {
            if (f.name.isBlank()) put("name", "Product name is required")
            if (f.minStock.isNotBlank() && f.minStock.toIntOrNull() == null) put("minStock", "Enter a whole number")
            if (f.salePrice.isNotBlank() && f.salePrice.toDoubleOrNull()?.let { it >= 0 } != true) put("salePrice", "Enter a valid price")
            if (f.purchasePrice.isNotBlank() && f.purchasePrice.toDoubleOrNull()?.let { it >= 0 } != true) put("purchasePrice", "Enter a valid price")
            if (f.taxRate.isNotBlank() && f.taxRate.toDoubleOrNull()?.let { it in 0.0..100.0 } != true) put("taxRate", "Enter 0 to 100")
            if (editId == null && f.openingQty.isNotBlank() && f.openingQty.toDoubleOrNull()?.let { it >= 0 } != true) {
                put("openingStock.quantity", "Enter a valid quantity")
            }
            putAll(Attributes.validate(schema.fields, f.attributes))
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, error = "Please correct the highlighted fields") }
            return
        }
        val qty = f.openingQty.toDoubleOrNull() ?: 0.0
        val serials = f.serials.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
        val request = ProductRequest(
            name = f.name.trim(), categoryId = f.categoryId, baseUnitId = f.unitId, brand = f.brand.blankToNull(),
            model = f.model.blankToNull(), description = f.description.blankToNull(), minStock = f.minStock.toIntOrNull(),
            attributes = Attributes.toJson(schema.fields, f.attributes), sku = f.sku.blankToNull(),
            barcode = f.barcode.blankToNull(), trackBatches = f.trackBatches && schema.trackBatches,
            salePrice = f.salePrice.toDoubleOrNull(),
            purchasePrice = f.purchasePrice.toDoubleOrNull(),
            taxRate = f.taxRate.toDoubleOrNull(),
            supplierId = f.supplierId,
            trackSerials = f.trackSerials && schema.trackSerials,
            openingStock = if (editId == null && qty > 0) OpeningStock(
                quantity = qty, warehouseId = f.warehouseId, lotNumber = f.lotNumber.blankToNull(),
                expiryDate = f.expiryDate.blankToNull(), serials = serials.ifEmpty { null },
            ) else null,
        )
        _state.update { it.copy(saving = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            when (val r = apiCall { if (editId == null) api.createProduct(request) else api.updateProduct(editId, request) }) {
                is ApiResult.Ok -> _state.update { it.copy(saving = false, savedId = r.value.id) }
                is ApiResult.Err -> _state.update { it.copy(saving = false, error = r.message, fieldErrors = r.fieldErrors) }
            }
        }
    }
}

@Composable
fun ProductFormScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel { c, s -> ProductFormViewModel(c.api, s) }
    val form by vm.form.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val schema by vm.schema.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val warehouses by vm.warehouses.collectAsStateWithLifecycle()
    val me = LocalMe.current
    var more by remember { mutableStateOf(vm.editId != null) }
    var currency by remember { mutableStateOf("") }
    var suppliers by remember { mutableStateOf<List<Party>>(emptyList()) }
    val container = appContainer()
    val canSeeCost = me?.can("ANALYTICS_VIEW") == true || me?.can("PRODUCT_EDIT") == true || me?.can("PURCHASE_MANAGE") == true
    LaunchedEffect(Unit) {
        (apiCall { container.api.settings() } as? ApiResult.Ok)?.let { currency = it.value.currencyCode }
        (apiCall { container.api.suppliers() } as? ApiResult.Ok)?.let { suppliers = it.value }
    }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    val e = state.fieldErrors

    Scaffold(topBar = { AppTopBar(if (vm.editId == null) "Add product" else "Edit product", onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.loadError != null -> ErrorState(state.loadError!!, vm::load, Modifier.padding(padding))
            else -> FormColumn(padding) {
                ErrorBanner(state.error)
                Text("Fields marked * are required.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                SectionCard("Basic information") {
                FormField(form.name, { v -> vm.update { it.copy(name = v) } }, "Product name", required = true, error = e["name"],
                    placeholder = "e.g. Basmati Rice 5 kg")
                DropdownField("Category", categories, categories.firstOrNull { it.id == form.categoryId }, { vm.setCategory(it.id) },
                    { it.path.ifEmpty { it.name } }, error = e["categoryId"], noneLabel = "No category", onNone = { vm.setCategory(null) })
                DropdownField("Unit of measurement", units, units.firstOrNull { it.id == form.unitId },
                    { u -> vm.update { it.copy(unitId = u.id) } }, { "${it.name} (${it.code})" }, error = e["baseUnitId"], required = true)
                FormField(form.sku, { v -> vm.update { it.copy(sku = v) } }, "Product code / SKU", error = e["sku"],
                    placeholder = "e.g. RICE-5KG", supporting = "Optional. Must be unique in your business.")
                }

                SectionCard("Stock") {
                if (vm.editId == null && me?.can("STOCK_ADD") == true) {
                    FormField(form.openingQty, { v -> vm.update { it.copy(openingQty = v) } }, "Opening stock",
                        keyboardType = KeyboardType.Decimal, error = e["openingStock.quantity"] ?: e["quantity"],
                        placeholder = "e.g. 50", supporting = "How many you have right now. Leave empty for none.")
                }
                FormField(form.minStock, { v -> vm.update { it.copy(minStock = v.filter(Char::isDigit)) } }, "Minimum stock level",
                    keyboardType = KeyboardType.Number, error = e["minStock"], placeholder = "e.g. 10",
                    supporting = "You get a low-stock alert at this level. Leave empty to use the business default.")
                }

                SectionCard("Prices (per ${units.firstOrNull { it.id == form.unitId }?.code ?: "unit"})") {
                val cur = if (currency.isNotBlank()) " ($currency)" else ""
                if (canSeeCost) {
                    FormField(form.purchasePrice, { v -> vm.update { it.copy(purchasePrice = v) } }, "Purchase price$cur",
                        keyboardType = KeyboardType.Decimal, error = e["purchasePrice"], placeholder = "e.g. 80",
                        supporting = "What you pay the dealer. Used for profit until you record purchases.")
                }
                FormField(form.salePrice, { v -> vm.update { it.copy(salePrice = v) } }, "Selling price$cur",
                    keyboardType = KeyboardType.Decimal, error = e["salePrice"], placeholder = "e.g. 120",
                    supporting = "Filled in automatically when you record a sale.")
                val buy = form.purchasePrice.toDoubleOrNull()
                val sell = form.salePrice.toDoubleOrNull()
                if (canSeeCost && buy != null && sell != null) {
                    val per = sell - buy
                    MessageBanner(if (per >= 0) BannerKind.SUCCESS else BannerKind.ERROR,
                        "Profit per unit: ${Fmt.qty(Math.round(per * 100) / 100.0)}" +
                        (if (sell > 0) " (${Fmt.qty(Math.round(per / sell * 1000) / 10.0)}% margin)" else ""))
                }
                FormField(form.taxRate, { v -> vm.update { it.copy(taxRate = v) } }, "Tax rate % (optional)",
                    keyboardType = KeyboardType.Decimal, error = e["taxRate"], placeholder = "e.g. 18",
                    supporting = "Added on top of the price when you sell. Leave empty if you don't charge tax.")
                if (suppliers.isNotEmpty()) {
                    DropdownField("Supplier (optional)", suppliers, suppliers.firstOrNull { it.id == form.supplierId },
                        { s -> vm.update { it.copy(supplierId = s.id) } }, { it.name }, noneLabel = "No supplier",
                        onNone = { vm.update { it.copy(supplierId = null) } }, error = e["supplierId"])
                }
                }

                if (schema.fields.isNotEmpty()) {
                    SectionCard(if (form.categoryId == null) "Details" else "Details for this category") {
                        schema.fields.forEach { def ->
                            AttributeInput(def, form.attributes[def.key], e["attributes.${def.key}"]) { vm.setAttribute(def.key, it) }
                        }
                    }
                }

                TextButton(onClick = { more = !more }) {
                    Text(if (more) "Hide extra details" else "More details (brand, barcode, description…)")
                    Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                AnimatedVisibility(more) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FormField(form.brand, { v -> vm.update { it.copy(brand = v) } }, "Brand", error = e["brand"])
                        FormField(form.model, { v -> vm.update { it.copy(model = v) } }, "Model", error = e["model"])
                        FormField(form.barcode, { v -> vm.update { it.copy(barcode = v) } }, "Barcode", error = e["barcode"])
                        FormField(form.description, { v -> vm.update { it.copy(description = v) } }, "Description",
                            singleLine = false, minLines = 3, error = e["description"])
                        if (schema.trackBatches) {
                            SwitchRow("Track batches / lots", form.trackBatches, { v -> vm.update { it.copy(trackBatches = v) } },
                                "Each stock receipt records a lot number" + if (schema.trackExpiry) " and expiry date" else "")
                        }
                        if (schema.trackSerials) {
                            SwitchRow("Track serial numbers", form.trackSerials, { v -> vm.update { it.copy(trackSerials = v) } },
                                "Every unit is recorded by serial number")
                        }
                    }
                }

                val qty = form.openingQty.toDoubleOrNull() ?: 0.0
                if (vm.editId == null && qty > 0) {
                    SectionCard("Opening stock") {
                        if (warehouses.size > 1) {
                            DropdownField("Warehouse", warehouses, warehouses.firstOrNull { it.id == form.warehouseId },
                                { w -> vm.update { it.copy(warehouseId = w.id) } }, { it.name })
                        }
                        if (form.trackBatches && schema.trackBatches) {
                            FormField(form.lotNumber, { v -> vm.update { it.copy(lotNumber = v) } }, "Lot number", required = true,
                                error = e["lotNumber"])
                            if (schema.trackExpiry) {
                                DateField("Expiry date", form.expiryDate, { v -> vm.update { it.copy(expiryDate = v) } },
                                    error = e["expiryDate"], required = true)
                            }
                        }
                        if (form.trackSerials && schema.trackSerials) {
                            FormField(form.serials, { v -> vm.update { it.copy(serials = v) } }, "Serial numbers", required = true,
                                singleLine = false, minLines = 3, error = e["serials"], supporting = "One per line")
                        }
                    }
                }
                PrimaryButton(if (vm.editId == null) "Save product" else "Save changes", vm::save, loading = state.saving,
                    icon = Icons.Default.Check)
                SecondaryButton("Cancel", onBack, enabled = !state.saving)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
