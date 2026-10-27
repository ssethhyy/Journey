/*
 * Feature Module: myki
 * File: MykiSheets.kt
 * Description: NFC scanning hook and the sheets used by the myki screens: add a card, update the
 * balance, and add money (record a top up or go to PTV).
 */

package com.ssethhyy.journey.ui.screens.myki

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.data.myki.MykiNfc
import com.ssethhyy.journey.data.myki.MykiNumber
import com.ssethhyy.journey.data.myki.MykiScan
import com.ssethhyy.journey.data.myki.formatDollars
import com.ssethhyy.journey.data.myki.parseDollars
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.components.Sym

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class NfcState { UNSUPPORTED, OFF, READY }

/** Whether this phone can scan a myki right now (re-checked when the screen resumes). */
@Composable
fun rememberNfcState(): NfcState {
    val activity = LocalContext.current.findActivity()
    val owner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(NfcState.UNSUPPORTED) }
    DisposableEffect(activity, owner) {
        fun check() {
            state = when {
                activity == null || !MykiNfc.isSupported(activity) -> NfcState.UNSUPPORTED
                !MykiNfc.isEnabled(activity) -> NfcState.OFF
                else -> NfcState.READY
            }
        }
        check()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) check() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Listens for a myki tapped on the phone while this screen is resumed. Results arrive on the main thread. */
@Composable
fun MykiScanEffect(enabled: Boolean, onScan: (MykiScan) -> Unit) {
    val activity = LocalContext.current.findActivity() ?: return
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onScan)
    DisposableEffect(activity, owner, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val main = Handler(Looper.getMainLooper())
        val start = { MykiNfc.enable(activity) { result -> main.post { latest(result) } } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> MykiNfc.disable(activity)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            MykiNfc.disable(activity)
        }
    }
}

fun openNfcSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Groups digits as printed on the card while the field keeps plain digits. */
private object MykiNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = MykiNumber.format(text.text)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                // Count characters in the formatted string until `offset` digits have passed.
                var digits = 0
                formatted.forEachIndexed { i, c ->
                    if (c.isDigit()) digits++
                    if (digits == offset) return i + 1
                }
                return formatted.length
            }

            override fun transformedToOriginal(offset: Int): Int = formatted.take(offset).count(Char::isDigit)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/** "Hold your myki to the phone" panel with a gentle pulse while listening. */
@Composable
fun ScanPrompt(nfc: NfcState, message: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val pulse by rememberInfiniteTransition(label = "nfc").animateFloat(
                initialValue = 0.9f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "nfc_pulse",
            )
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Sym.Contactless, null, Modifier.size(28.dp).scale(if (nfc == NfcState.READY) pulse else 1f))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (nfc) {
                        NfcState.READY -> "Hold your myki to the back of your phone"
                        NfcState.OFF -> "Turn on NFC to scan your myki"
                        NfcState.UNSUPPORTED -> "This phone can't scan cards"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    message ?: when (nfc) {
                        NfcState.READY -> "Journey reads the card number"
                        NfcState.OFF -> "Or type the number below"
                        NfcState.UNSUPPORTED -> "Type the number from the back of the card"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            if (nfc == NfcState.OFF) {
                TextButton(onClick = { openNfcSettings(context) }) { Text("Turn on") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddMykiSheet(
    scannedNumber: String?,
    scanMessage: String?,
    nfc: NfcState,
    onDismiss: () -> Unit,
    onSave: (number: String, nickname: String, fareType: MykiFareType, balanceCents: Long?) -> Unit,
) {
    var number by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var fareType by rememberSaveable { mutableStateOf(MykiFareType.FULL) }
    var balance by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scannedNumber) { scannedNumber?.let { number = it; touched = true } }

    val problem = MykiNumber.problem(number)
    val balanceCents = parseDollars(balance)
    val balanceError = balance.isNotBlank() && balanceCents == null

    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true, title = "Add a myki") {
        ScanPrompt(nfc, scanMessage)
        OutlinedTextField(
            value = number,
            onValueChange = { input ->
                number = MykiNumber.digits(input).take(MykiNumber.LENGTH)
                if (number.length == MykiNumber.LENGTH) touched = true
            },
            label = { Text("Card number") },
            placeholder = { Text("3 08425 0000 0000 0") },
            visualTransformation = MykiNumberTransformation,
            isError = touched && problem != null,
            supportingText = { Text(if (touched || number.isNotEmpty()) problem ?: "Looks good" else "On the back of your myki") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it.take(24) },
            label = { Text("Name (optional)") },
            placeholder = { Text("My myki") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Fare type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MykiFareType.entries.forEach { type ->
                FilterChip(selected = fareType == type, onClick = { fareType = type }, label = { Text(type.label) })
            }
        }
        OutlinedTextField(
            value = balance,
            onValueChange = { balance = it.take(9) },
            label = { Text("Current balance (optional)") },
            prefix = { Text("$") },
            isError = balanceError,
            supportingText = { Text(if (balanceError) "Enter an amount like 26.16" else "Check it at a myki machine or in the PTV app") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(number, nickname, fareType, balanceCents) },
            enabled = problem == null && !balanceError,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Sym.AddCard, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add myki")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheet(currentCents: Long?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(currentCents?.let { formatDollars(it).removePrefix("$").removePrefix("−$") } ?: "") }
    val cents = parseDollars(text)
    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true, title = "Update balance") {
        Text(
            "myki keeps your balance locked on the card, so Journey can't read it. Check it at a myki machine, on a reader, or in the PTV app, then enter it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(9) },
            label = { Text("Balance") },
            prefix = { Text("$") },
            isError = text.isNotBlank() && cents == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { cents?.let(onSave) }, enabled = cents != null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save balance") }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddMoneySheet(onDismiss: () -> Unit, onOpenPtv: () -> Unit, onRecord: (Long) -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val cents = if (custom.isNotBlank()) parseDollars(custom) else selected
    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true, title = "Add money or pass") {
        Surface(
            onClick = onOpenPtv,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Wallet, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Top up with PTV", style = MaterialTheme.typography.titleSmall)
                    Text("Opens the PTV app, or the myki website", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Sym.ChevronRight, null)
            }
        }
        Text("Already topped up? Record it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1_000L, 2_000L, 5_000L, 10_000L).forEach { amount ->
                FilterChip(
                    selected = custom.isBlank() && selected == amount,
                    onClick = { selected = amount; custom = "" },
                    label = { Text(formatDollars(amount).removeSuffix(".00")) },
                )
            }
        }
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it.take(9) },
            label = { Text("Other amount") },
            prefix = { Text("$") },
            isError = custom.isNotBlank() && (cents == null || cents <= 0),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { cents?.takeIf { it > 0 }?.let(onRecord) },
            enabled = cents != null && cents > 0,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(cents?.takeIf { it > 0 }?.let { "Record ${formatDollars(it)} top up" } ?: "Record top up") }
    }
}

/** Opens the PTV app when installed, otherwise the official myki top-up page. */
fun openPtvTopUp(context: Context) {
    val app = context.packageManager.getLaunchIntentForPackage("au.gov.vic.ptv")
    val intent = app ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://transport.vic.gov.au/tickets-and-payments/myki/top-up-your-myki"))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
