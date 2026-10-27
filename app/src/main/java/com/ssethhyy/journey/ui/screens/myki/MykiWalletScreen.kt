/*
 * Feature Module: myki
 * File: MykiWalletScreen.kt
 * Description: The For You FAB destination. Cards stacked like a wallet (tap one to open it), an
 * "Add myki" action, and an empty state. Tapping a physical myki on the phone opens or adds it.
 */

package com.ssethhyy.journey.ui.screens.myki

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiScan
import com.ssethhyy.journey.data.myki.formatDollars
import com.ssethhyy.journey.ui.appViewModel
import com.ssethhyy.journey.ui.components.LoadingState
import com.ssethhyy.journey.ui.components.ReusableTopAppBar
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.navigation.currentNavigator
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.launch

/** How much of each card behind the front one stays visible: enough for its name and wordmark. */
private const val STACK_PEEK_FRACTION = 0.26f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MykiWalletScreen() {
    val vm = appViewModel { MykiViewModel(it) }
    val cards by vm.cards.collectAsStateWithLifecycle()
    val navigator = currentNavigator
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val nfc = rememberNfcState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<String?>(null) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    // A tapped myki opens its card, or starts adding it.
    MykiScanEffect(enabled = nfc == NfcState.READY) { result ->
        when (result) {
            is MykiScan.Card -> {
                HapticUtil.performUIHaptic(view)
                val existing = vm.cardFor(result.number)
                if (existing != null && !adding) {
                    navigator.openMykiCard(existing.id)
                } else {
                    scanned = result.number
                    scanMessage = if (existing != null) "That myki is already in Journey" else "Card number read"
                    adding = true
                }
            }
            MykiScan.NotMyki -> if (adding) scanMessage = "That card isn't a myki" else scope.launch { snackbar.showSnackbar("That card isn't a myki") }
            MykiScan.Lost -> if (adding) scanMessage = "Hold the card still until it's read"
        }
    }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "myki",
                subtitle = when (val n = cards?.size ?: 0) {
                    0 -> "Your cards"
                    1 -> "1 card"
                    else -> "$n cards"
                },
                onBackClick = navigator::back,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val list = cards
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyWallet(
                nfc = nfc,
                onAdd = { scanned = null; scanMessage = null; adding = true },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().floatingBarBlur(padding.calculateTopPadding()),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "stack") {
                    CardStack(list, onOpen = { navigator.openMykiCard(it.id) })
                }
                item(key = "add") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                HapticUtil.performUIHaptic(view)
                                scanned = null
                                scanMessage = null
                                adding = true
                            },
                            modifier = Modifier.height(52.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                        ) {
                            Icon(Sym.AddCard, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add myki")
                        }
                        if (nfc == NfcState.READY) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Or hold a myki to your phone to open it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddMykiSheet(
            scannedNumber = scanned,
            scanMessage = scanMessage,
            nfc = nfc,
            onDismiss = { adding = false },
            onSave = { number, nickname, fare, balance ->
                vm.add(number, nickname, fare, balance) { id ->
                    adding = false
                    navigator.openMykiCard(id)
                }
            },
        )
    }
}

/** Wallet-style stack: earlier cards peek out above the front (last) card, which is fully visible. */
@Composable
private fun CardStack(cards: List<MykiCard>, onOpen: (MykiCard) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val peek = maxWidth * STACK_PEEK_FRACTION
        cards.forEachIndexed { index, card ->
            StackedCard(card, onOpen = { onOpen(card) }, modifier = Modifier.padding(top = peek * index))
        }
    }
}

@Composable
private fun StackedCard(card: MykiCard, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "card_press")
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(8.dp, shape)
            .clickable(interactionSource = interaction, indication = null) {
                HapticUtil.performUIHaptic(view)
                onOpen()
            },
    ) {
        MykiCardArt(card)
        // Name and balance sit in the strip that stays visible when another card is in front.
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
            modifier = Modifier.padding(14.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (card.isDefault) {
                    Icon(Sym.StarFilled, contentDescription = "Default myki", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    buildString {
                        append(card.nickname)
                        card.balanceCents?.let { append(" · ").append(formatDollars(it)) }
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyWallet(nfc: NfcState, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Sym.CreditCard, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("No mykis added yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (nfc == NfcState.READY) "Hold your myki to the back of your phone, or add it by number to keep its balance and top ups here."
            else "Add your myki by number to keep its balance and top ups here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                HapticUtil.performUIHaptic(view)
                onAdd()
            },
            modifier = Modifier.height(56.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
        ) {
            Icon(Sym.AddCard, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Add myki", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(96.dp))
    }
}
