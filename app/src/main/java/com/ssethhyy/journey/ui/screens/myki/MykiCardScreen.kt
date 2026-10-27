/*
 * Feature Module: myki
 * File: MykiCardScreen.kt
 * Description: One myki, laid out like a wallet transit card: card face, number, balance,
 * a tip about where balances come from, activity, and "Add money or pass".
 */

package com.ssethhyy.journey.ui.screens.myki

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import com.ssethhyy.journey.ui.components.floatingBarBlur
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.data.myki.MykiActivity
import com.ssethhyy.journey.data.myki.MykiActivityKind
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.data.myki.formatDollars
import com.ssethhyy.journey.data.myki.parseDollars
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.SectionLabel
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.navigation.MykiCardKey
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val MELBOURNE: ZoneId = ZoneId.of("Australia/Melbourne")

object MykiDates {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    fun day(millis: Long, now: Instant = Instant.now()): String {
        val date = Instant.ofEpochMilli(millis).atZone(MELBOURNE).toLocalDate()
        val today = now.atZone(MELBOURNE).toLocalDate()
        return when (ChronoUnit.DAYS.between(date, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> date.format(dayMonth)
        }
    }

    fun activity(millis: Long, now: Instant = Instant.now()): String =
        "${day(millis, now)} · ${TimeFormatter.clock(Instant.ofEpochMilli(millis))}"

    fun updated(millis: Long, now: Instant = Instant.now()): String {
        val age = Duration.between(Instant.ofEpochMilli(millis), now)
        return when {
            age.toMinutes() < 1 -> "Updated just now"
            age.toMinutes() < 60 -> "Updated ${age.toMinutes()} min ago"
            day(millis, now) == "Today" -> "Updated today at ${TimeFormatter.clock(Instant.ofEpochMilli(millis))}"
            else -> "Updated ${day(millis, now).replaceFirstChar { it.lowercase() }.let { if (it == "yesterday") it else "on $it" }}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MykiCardScreen(key: MykiCardKey) {
    val vm = appViewModel { MykiViewModel(it) }
    val cards by vm.cards.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val context = LocalContext.current
    var menu by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    val card = cards?.firstOrNull { it.id == key.cardId }
    // Removed (here or elsewhere): leave the screen.
    LaunchedEffect(cards, card) { if (cards != null && card == null) navigator.back() }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = card?.nickname ?: "myki",
                onBackClick = navigator::back,
                actions = {
                    Box {
                        HapticIconButton(Sym.MoreVert, "More options", onClick = { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (card != null && !card.isDefault) {
                                DropdownMenuItem(text = { Text("Make default") }, leadingIcon = { Icon(Sym.StarFilled, null) }, onClick = { menu = false; vm.makeDefault(card.id) })
                            }
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Sym.Edit, null) }, onClick = { menu = false; dialog = "rename" })
                            DropdownMenuItem(text = { Text("Fare type") }, leadingIcon = { Icon(Sym.Person, null) }, onClick = { menu = false; dialog = "fare" })
                            DropdownMenuItem(text = { Text("Update balance") }, leadingIcon = { Icon(Sym.Toll, null) }, onClick = { menu = false; sheet = "balance" })
                            DropdownMenuItem(text = { Text("Low balance alert") }, leadingIcon = { Icon(Sym.Warning, null) }, onClick = { menu = false; dialog = "low" })
                            DropdownMenuItem(text = { Text("Fares and discounts") }, leadingIcon = { Icon(Sym.ReceiptLong, null) }, onClick = { menu = false; navigator.openFares() })
                            DropdownMenuItem(text = { Text("Remove from Journey") }, leadingIcon = { Icon(Sym.Delete, null) }, onClick = { menu = false; dialog = "remove" })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (card != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { sheet = "log" },
                            modifier = Modifier.weight(1f).height(60.dp),
                        ) { Text("Log trip", style = MaterialTheme.typography.titleMedium) }
                        Button(
                            onClick = { sheet = "money" },
                            modifier = Modifier.weight(1.5f).height(60.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) { Text("Add money or pass", style = MaterialTheme.typography.titleMedium, maxLines = 1) }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        if (card == null) {
            LoadingState()
            return@Scaffold
        }
        CardContent(
            card = card,
            contentPadding = padding,
            onBalance = { sheet = "balance" },
            onDismissTip = { vm.dismissTip(card.id) },
        )
    }

    if (card != null) {
        when (sheet) {
            "balance" -> BalanceSheet(card.balanceCents, onDismiss = { sheet = null }) { vm.setBalance(card.id, it); sheet = null }
            "log" -> LogManualTripSheet(card, onDismiss = { sheet = null })
            "money" -> AddMoneySheet(
                onDismiss = { sheet = null },
                onOpenPtv = { openPtvTopUp(context) },
                onRecord = { vm.topUp(card.id, it); sheet = null },
            )
        }
        when (dialog) {
            "rename" -> RenameMykiDialog(card.nickname, onDismiss = { dialog = null }) { vm.rename(card.id, it); dialog = null }
            "low" -> LowBalanceDialog(card.lowBalanceCents, onDismiss = { dialog = null }) { vm.setLowBalance(card.id, it); dialog = null }
            "fare" -> FareTypeDialog(card.fareType, onDismiss = { dialog = null }) { vm.setFareType(card.id, it); dialog = null }
            "remove" -> AlertDialog(
                onDismissRequest = { dialog = null },
                icon = { Icon(Sym.Delete, null) },
                title = { Text("Remove ${card.nickname}?") },
                text = { Text("This only removes the card and its activity from Journey. Your myki and its balance aren't affected.") },
                confirmButton = { TextButton(onClick = { dialog = null; vm.remove(card.id) }) { Text("Remove") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun CardContent(card: MykiCard, contentPadding: PaddingValues, onBalance: () -> Unit, onDismissTip: () -> Unit) {
    val view = LocalView.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize().floatingBarBlur(contentPadding.calculateTopPadding()),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "art") { MykiCardArt(card, Modifier.padding(horizontal = 8.dp), cornerRadius = 24.dp) }
        item(key = "number") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        HapticUtil.performUIHaptic(view)
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("myki number", card.number))) }
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Sym.CreditCard, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(card.formattedNumber, style = MaterialTheme.typography.titleMedium, letterSpacing = 0.5.sp)
                if (card.fareType != MykiFareType.FULL) {
                    Text(" · ${card.fareType.label}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (card.isDefault) {
            item(key = "default") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Sym.StarFilled, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Default myki", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        item(key = "balance") { BalanceCard(card, onBalance) }
        if (!card.tipDismissed) {
            item(key = "tip") { BalanceTip(onUpdate = onBalance, onDismiss = onDismissTip) }
        }
        item(key = "activity_header") { SectionLabel("Activity", Modifier.padding(top = 4.dp)) }
        if (card.activity.isEmpty()) {
            item(key = "activity_empty") {
                Text(
                    "Top ups and balance updates you record will show here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            item(key = "activity") {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        card.activity.take(30).forEach { ActivityRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(card: MykiCard, onClick: () -> Unit) {
    val view = LocalView.current
    Surface(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Balance", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (card.isLow) "Low balance · alert below ${formatDollars(card.lowBalanceCents)}"
                    else card.balanceUpdatedMillis?.let { MykiDates.updated(it) } ?: "Tap to add your balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (card.isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                card.balanceCents?.let { formatDollars(it) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BalanceTip(onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(Modifier.padding(start = 24.dp, top = 22.dp, end = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Keep your balance up to date after you check it at a myki machine or in the PTV app",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = onUpdate, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text("Update balance")
                    }
                }
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Sym.Toll, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            HapticIconButton(
                Sym.Close,
                "Dismiss",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityRow(entry: MykiActivity) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (entry.kind) {
                    MykiActivityKind.TOP_UP -> Sym.Add
                    MykiActivityKind.TRIP -> Sym.Route
                    MykiActivityKind.CHARGE -> Sym.Toll
                    MykiActivityKind.BALANCE -> Sym.Toll
                },
                null,
                Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title ?: entry.kind.label, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                buildString {
                    entry.detail?.let { append(it).append(" · ") }
                    append(MykiDates.activity(entry.atMillis))
                    entry.balanceAfterCents?.let { append(" · Balance ").append(formatDollars(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when {
                entry.kind == MykiActivityKind.TRIP && entry.chargedCents == 0L -> "Free"
                entry.amountCents != null -> formatDollars(entry.amountCents, signed = true)
                else -> entry.balanceAfterCents?.let { formatDollars(it) } ?: ""
            },
            style = MaterialTheme.typography.titleMedium,
            color = if ((entry.amountCents ?: 0) > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LowBalanceDialog(current: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) formatDollars(current).removePrefix("$") else "") }
    val cents = if (text.isBlank()) 0L else parseDollars(text)
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Sym.Warning, null) },
        title = { Text("Low balance alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Get a notification when this myki drops below an amount. Leave it empty to turn the alert off.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(7) },
                    label = { Text("Alert below") },
                    prefix = { Text("$") },
                    isError = cents == null,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    cents?.let {
                        if (it > 0 && android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        onConfirm(it)
                    }
                },
                enabled = cents != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameMykiDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Sym.Edit, null) },
        title = { Text("Rename myki") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(24) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FareTypeDialog(current: MykiFareType, onDismiss: () -> Unit, onConfirm: (MykiFareType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fare type") },
        text = {
            Column {
                MykiFareType.entries.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onConfirm(type) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == current, onClick = { onConfirm(type) })
                        Text(type.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
