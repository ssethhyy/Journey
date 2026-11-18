/*
 * Feature Module: myki
 * File: AddMykiSheet.kt
 * Description: Adding a myki. It opens ready to scan, like the system's NFC sheet but in Material 3 Expressive: a card
 * with Open Transit's myki NFC animation (by Aston Bolwell, https://github.com/AstonBolwell/Open-Transit-App) and a
 * prompt to hold the card to the phone. Reading a card, or choosing "Add manually", moves on to its details, with the
 * card face filling in above them.
 */

package com.ssethhyy.journey.ui.screens.myki

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.ssethhyy.journey.R
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.data.myki.MykiNumber
import com.ssethhyy.journey.data.myki.parseDollars
import com.ssethhyy.journey.ui.components.StandardBottomSheet
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.hapticConfirm
import com.ssethhyy.journey.ui.components.hapticSelect
import com.ssethhyy.journey.utils.HapticUtil

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddMykiSheet(
    scannedNumber: String?,
    scanMessage: String?,
    nfc: NfcState,
    onDismiss: () -> Unit,
    onSave: (number: String, nickname: String, fareType: MykiFareType, balanceCents: Long?) -> Unit,
) {
    // Scanning first when the phone can; straight to typing it in when it can't.
    var manual by rememberSaveable { mutableStateOf(nfc == NfcState.UNSUPPORTED) }
    var number by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var fareType by rememberSaveable { mutableStateOf(MykiFareType.FULL) }
    var balance by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    LaunchedEffect(scannedNumber) {
        scannedNumber?.let {
            number = it
            touched = true
            HapticUtil.performConfirmHaptic(view)
        }
    }
    val scanning = !manual && scannedNumber == null

    StandardBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        AnimatedContent(
            targetState = scanning,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.94f)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMedium))
            },
            label = "add_myki_step",
        ) { isScanning ->
            if (isScanning) {
                ReadyToScan(nfc = nfc, message = scanMessage, onManual = { manual = true }, onCancel = onDismiss)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val problem = MykiNumber.problem(number)
                    val balanceCents = parseDollars(balance)
                    val balanceError = balance.isNotBlank() && balanceCents == null
                    Text(
                        if (scannedNumber != null) "myki read" else "Add a myki",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // The card fills in as you go.
                    MykiCardFace(
                        number = number.takeIf { it.isNotBlank() },
                        fareLabel = fareType.takeIf { it != MykiFareType.FULL }?.label,
                        cornerRadius = 22.dp,
                    )
                    scanMessage?.takeIf { scannedNumber != null }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
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
                            FilterChip(selected = fareType == type, onClick = hapticSelect { fareType = type }, label = { Text(type.label) })
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
                        onClick = hapticConfirm { onSave(number, nickname, fareType, balanceCents) },
                        enabled = problem == null && !balanceError,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Sym.AddCard, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add myki")
                    }
                    if (nfc != NfcState.UNSUPPORTED && scannedNumber == null) {
                        TextButton(onClick = { manual = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Sym.Contactless, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan it instead")
                        }
                    }
                }
            }
        }
    }
}

/**
 * The scan step: "Ready to scan" over a large card holding the NFC animation, what's happening below it, then "Add
 * manually" and "Cancel", the way the system's NFC sheet is laid out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReadyToScan(nfc: NfcState, message: String?, onManual: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            if (nfc == NfcState.OFF) "Turn on NFC" else "Ready to scan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (nfc == NfcState.OFF) "NFC is off, so Journey can't read your myki yet" else "Hold your myki to the back of your phone",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                MykiNfcAnimation(playing = nfc == NfcState.READY, modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(1f))
            }
        }
        Spacer(Modifier.height(20.dp))
        // What just happened ("That card isn't a myki"), or a nudge.
        Text(
            message ?: if (nfc == NfcState.READY) "Keep it still until it's read" else " ",
            style = MaterialTheme.typography.bodyMedium,
            color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = message ?: "Waiting for a myki" },
        )
        Spacer(Modifier.height(20.dp))
        if (nfc == NfcState.OFF) {
            Button(onClick = hapticConfirm { openNfcSettings(context) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Sym.Contactless, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Turn on NFC")
            }
            Spacer(Modifier.height(8.dp))
        }
        FilledTonalButton(onClick = hapticConfirm(onManual), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Sym.Edit, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add manually")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Cancel") }
    }
}

/**
 * Open Transit's myki NFC animation, recoloured to match: the phone and its waves in the theme's primary colour, and
 * the little myki in the card face's colours.
 */
@Composable
fun MykiNfcAnimation(playing: Boolean, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.myki_nfc))
    val scheme = MaterialTheme.colorScheme
    val card = MykiCardColours.current()
    fun argb(c: Color) = c.toArgb()
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, argb(scheme.primary), "phone (Group)", "**"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, argb(scheme.primary), "phone (Group)", "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, argb(scheme.primary), "Path 8243", "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, argb(scheme.primary), "Path 8242", "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, argb(scheme.surfaceContainerHighest), "backgrond", "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, argb(card.card), "Group Layer 1", "**", "Rectangle 2676", "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, argb(card.wordmark), "Group Layer 1", "**", "Rectangle 2739", "**"),
    )
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = playing,
        dynamicProperties = dynamicProperties,
        modifier = modifier.semantics { contentDescription = "Hold your myki to the back of your phone" },
    )
}
