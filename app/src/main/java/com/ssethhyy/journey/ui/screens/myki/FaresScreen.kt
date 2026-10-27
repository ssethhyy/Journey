/*
 * Feature Module: myki
 * File: FaresScreen.kt
 * Description: The fare table and discount rules behind trip logging. Built-in discounts (half-price
 * fares, free travel for under 18s, Seniors weekends, Early Bird) can be switched off; custom ones
 * cover whatever the government announces next, such as a free-travel week or a new half-price
 * period. Prices can be edited when they change, and the app asks for a check after each 1 January.
 */

package com.ssethhyy.journey.ui.screens.myki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.fare.ClassPrices
import com.ssethhyy.journey.data.fare.DayKind
import com.ssethhyy.journey.data.fare.FareConfig
import com.ssethhyy.journey.data.fare.FareConfigs
import com.ssethhyy.journey.data.fare.FareRule
import com.ssethhyy.journey.data.fare.FareTable
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.data.myki.formatDollars
import com.ssethhyy.journey.data.myki.parseDollars
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.navigation.currentNavigator
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaresScreen() {
    val container = LocalContext.current.appContainer
    val repo = container.fareConfigRepository
    val config by repo.config.collectAsStateWithLifecycle(initialValue = FareConfig())
    val navigator = currentNavigator
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var editingRule by remember { mutableStateOf<FareRule?>(null) }
    var addingRule by rememberSaveable { mutableStateOf(false) }
    var editingPrices by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "Fares and discounts",
                subtitle = config.table.label,
                onBackClick = navigator::back,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (FareConfigs.needsReview(config, today)) {
                item(key = "review") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Check these prices", style = MaterialTheme.typography.titleMedium)
                            Text("Fares usually change on 1 January. Compare them with transport.vic.gov.au, then edit anything that's different.", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { scope.launch { repo.confirmPrices() } }) { Text("They're correct") }
                                TextButton(onClick = { editingPrices = true }) { Text("Edit prices") }
                            }
                        }
                    }
                }
            }

            item(key = "discounts") { SectionLabel("Discounts") }
            item(key = "discounts_note") {
                Text(
                    "When more than one applies, the biggest discount is used. Switch one off if it doesn't apply to you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(config.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    // "Active now" means it could apply today: switched on, within its dates, and on the right kind of day.
                    activeNow = rule.enabled && rule.isActiveOn(today) && when (rule.days) {
                        DayKind.ANY -> true
                        DayKind.WEEKDAY -> !com.ssethhyy.journey.data.fare.FareEngine.isWeekend(today)
                        DayKind.WEEKEND -> com.ssethhyy.journey.data.fare.FareEngine.isWeekend(today)
                    },
                    onToggle = { scope.launch { repo.setRuleEnabled(rule, it) } },
                    onEdit = if (rule.builtIn) null else ({ editingRule = rule }),
                )
            }
            item(key = "add_rule") {
                FilledTonalButton(onClick = { addingRule = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Sym.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add a discount")
                }
            }

            item(key = "prices") { SectionLabel("Prices", Modifier.padding(top = 8.dp)) }
            item(key = "table") { PriceTable(config.table, onEdit = { editingPrices = true }) }
            item(key = "footnote") {
                Text(
                    "Journey works out fares itself from these prices, as there is no official fares feed. It covers the 2-hour fare, daily and weekend caps, Zone 2, the Free Tram Zone and the discounts above. The weekly cap and regional V/Line fares aren't included, so V/Line trips ask for an amount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (addingRule || editingRule != null) {
        RuleSheet(
            initial = editingRule,
            onDismiss = { addingRule = false; editingRule = null },
            onSave = { rule ->
                scope.launch { repo.saveCustomRule(rule) }
                addingRule = false
                editingRule = null
            },
            onDelete = editingRule?.let { rule ->
                {
                    scope.launch { repo.removeCustomRule(rule.id) }
                    editingRule = null
                }
            },
        )
    }
    if (editingPrices) {
        PricesSheet(
            table = config.table,
            onDismiss = { editingPrices = false },
            onSave = { scope.launch { repo.setTable(it) }; editingPrices = false },
            onReset = { scope.launch { repo.resetTable() }; editingPrices = false },
        )
    }
}

private fun scopeText(rule: FareRule): String = buildList {
    add("${rule.percentOff}% off")
    if (rule.modes.isNotEmpty()) add(rule.modes.joinToString(", ") { it.label })
    if (rule.fareTypes.isNotEmpty()) add(rule.fareTypes.joinToString(", ") { it.label })
    if (rule.days != DayKind.ANY) add(rule.days.label)
    if (rule.beforeMinute != null) add("finishing before ${rule.beforeMinute / 60}:${(rule.beforeMinute % 60).toString().padStart(2, '0')}")
    if (rule.from != null || rule.to != null) add("${rule.from ?: "any date"} to ${rule.to ?: "no end"}")
}.joinToString(" · ")

@Composable
private fun RuleCard(rule: FareRule, activeNow: Boolean, onToggle: (Boolean) -> Unit, onEdit: (() -> Unit)?) {
    Surface(
        onClick = { onEdit?.invoke() },
        enabled = onEdit != null,
        shape = RoundedCornerShape(24.dp),
        color = if (activeNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (activeNow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (activeNow) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                            Text("Active now", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                if (rule.note.isNotBlank()) Text(rule.note, style = MaterialTheme.typography.bodySmall)
                Text(scopeText(rule), style = MaterialTheme.typography.labelSmall, color = LocalContentColorVariant())
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun LocalContentColorVariant() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun PriceTable(table: FareTable, onEdit: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1.6f))
                Text("Full", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Concession", Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            PriceRow("2 hours, Zone 1+2", table.full.twoHourZone12, table.concession.twoHourZone12)
            PriceRow("2 hours, Zone 2", table.full.twoHourZone2, table.concession.twoHourZone2)
            PriceRow("Daily cap, Zone 1+2", table.full.dailyCapZone12, table.concession.dailyCapZone12)
            PriceRow("Daily cap, Zone 2", table.full.dailyCapZone2, table.concession.dailyCapZone2)
            PriceRow("Weekend and holidays", table.full.weekendCapZone12, table.concession.weekendCapZone12)
            Text(
                "Child, Concession and Seniors myki all use concession prices. Children are free through the discount above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit prices") }
        }
    }
}

@Composable
private fun PriceRow(label: String, full: Long, concession: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.6f), style = MaterialTheme.typography.bodyMedium)
        Text(formatDollars(full), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatDollars(concession), Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(initial: FareRule?, onDismiss: () -> Unit, onSave: (FareRule) -> Unit, onDelete: (() -> Unit)?) {
    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }
    var percent by rememberSaveable { mutableStateOf((initial?.percentOff ?: 50).toString()) }
    var from by rememberSaveable { mutableStateOf(initial?.from ?: "") }
    var to by rememberSaveable { mutableStateOf(initial?.to ?: "") }
    var modes by remember { mutableStateOf(initial?.modes ?: emptySet()) }
    var types by remember { mutableStateOf(initial?.fareTypes ?: emptySet()) }
    var days by rememberSaveable { mutableStateOf(initial?.days ?: DayKind.ANY) }

    fun validDate(text: String) = text.isBlank() || runCatching { LocalDate.parse(text) }.isSuccess
    val percentValue = percent.toIntOrNull()
    val valid = label.isNotBlank() && percentValue != null && percentValue in 1..100 && validDate(from) && validDate(to)

    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true, title = if (initial == null) "Add a discount" else "Edit discount") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("For a government announcement like free or half-price travel, or anything else that changes what you pay.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Free travel" to 100, "Half price" to 50, "25% off" to 25).forEach { (name, value) ->
                    FilterChip(selected = false, onClick = { percent = value.toString(); if (label.isBlank()) label = name }, label = { Text(name) })
                }
            }
            OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = percent,
                onValueChange = { percent = it.filter(Char::isDigit).take(3) },
                label = { Text("Percent off (100 = free)") },
                suffix = { Text("%") },
                isError = percentValue == null || percentValue !in 1..100,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it.take(10) },
                    label = { Text("From") },
                    placeholder = { Text("2026-12-01") },
                    isError = !validDate(from),
                    supportingText = { Text("Optional") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it.take(10) },
                    label = { Text("Until (not included)") },
                    placeholder = { Text("2027-01-01") },
                    isError = !validDate(to),
                    supportingText = { Text("Optional") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Applies to (none selected = everything)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportMode.entries.filter { it != TransportMode.NIGHT_BUS }.forEach { m ->
                    FilterChip(selected = m in modes, onClick = { modes = if (m in modes) modes - m else modes + m }, label = { Text(m.label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MykiFareType.entries.forEach { t ->
                    FilterChip(selected = t in types, onClick = { types = if (t in types) types - t else types + t }, label = { Text(t.label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayKind.entries.forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text(d.label) }) }
            }
            Button(
                onClick = {
                    onSave(
                        FareRule(
                            id = initial?.id ?: "custom_${UUID.randomUUID()}",
                            label = label.trim(),
                            percentOff = percentValue ?: return@Button,
                            from = from.ifBlank { null },
                            to = to.ifBlank { null },
                            modes = modes,
                            fareTypes = types,
                            days = days,
                            enabled = initial?.enabled ?: true,
                        ),
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            onDelete?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Delete this discount") } }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PricesSheet(table: FareTable, onDismiss: () -> Unit, onSave: (FareTable) -> Unit, onReset: () -> Unit) {
    fun text(cents: Long) = formatDollars(cents).removePrefix("$")
    // Order: 2h Z1+2, 2h Z2, cap Z1+2, cap Z2, weekend  for full, then concession.
    val initial = listOf(table.full, table.concession).flatMap {
        listOf(it.twoHourZone12, it.twoHourZone2, it.dailyCapZone12, it.dailyCapZone2, it.weekendCapZone12)
    }.map(::text)
    var values by remember { mutableStateOf(initial) }
    var label by rememberSaveable { mutableStateOf(table.label) }
    val parsed = values.map(::parseDollars)
    val valid = parsed.all { it != null && it > 0 } && label.isNotBlank()
    val names = listOf("2 hours, Zone 1+2", "2 hours, Zone 2", "Daily cap, Zone 1+2", "Daily cap, Zone 2", "Weekend and holidays")

    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true, title = "Edit prices") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Name of this price list") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            listOf("Full fare", "Concession").forEachIndexed { group, groupName ->
                Text(groupName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                names.forEachIndexed { i, name ->
                    val index = group * 5 + i
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { v -> values = values.toMutableList().also { it[index] = v.take(7) } },
                        label = { Text(name) },
                        prefix = { Text("$") },
                        isError = parsed[index] == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Button(
                onClick = {
                    val p = parsed.map { it ?: return@Button }
                    fun prices(o: Int) = ClassPrices(p[o], p[o + 1], p[o + 2], p[o + 3], p[o + 4])
                    onSave(table.copy(label = label.trim(), effectiveFrom = LocalDate.now().toString(), full = prices(0), concession = prices(5)))
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save prices") }
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset to the app's prices") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
