/*
 * Feature Module: myki
 * File: MykiViewModel.kt
 * Description: Cards on this phone and the edits made from the wallet and card screens.
 */

package com.ssethhyy.journey.ui.screens.myki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.myki.MykiCard
import com.ssethhyy.journey.data.myki.MykiFareType
import com.ssethhyy.journey.data.myki.MykiLedger
import com.ssethhyy.journey.data.myki.MykiNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MykiViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.mykiRepository
    private val manager = container.mykiManager

    /** Null until the first read from storage, so the empty state never flashes. */
    val cards: StateFlow<List<MykiCard>?> = repository.cards
        .map<List<MykiCard>, List<MykiCard>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cardFor(number: String): MykiCard? = cards.value?.let { MykiLedger.find(it, number) }

    /** Saves a new card and returns its id (or the existing card's id when the number is already saved). */
    fun add(number: String, nickname: String, fareType: MykiFareType, balanceCents: Long?, onSaved: (String) -> Unit) {
        cardFor(number)?.let { return onSaved(it.id) }
        val now = System.currentTimeMillis()
        var card = MykiCard(
            id = MykiLedger.newId(),
            number = MykiNumber.digits(number),
            nickname = nickname.trim().ifEmpty { "myki" },
            fareType = fareType,
            addedMillis = now,
        )
        if (balanceCents != null) card = MykiLedger.setBalance(card, balanceCents, now)
        viewModelScope.launch {
            repository.add(card)
            onSaved(card.id)
        }
    }

    private fun edit(id: String, transform: (MykiCard) -> MykiCard) {
        viewModelScope.launch { repository.update(id, transform) }
    }

    fun rename(id: String, nickname: String) = edit(id) { it.copy(nickname = nickname.trim().ifEmpty { it.nickname }) }
    fun setFareType(id: String, type: MykiFareType) = edit(id) { it.copy(fareType = type) }
    fun setBalance(id: String, cents: Long) { viewModelScope.launch { manager.setBalance(id, cents) } }
    fun topUp(id: String, cents: Long) { viewModelScope.launch { manager.topUp(id, cents) } }
    fun makeDefault(id: String) { viewModelScope.launch { repository.setDefault(id) } }
    fun setLowBalance(id: String, cents: Long) { viewModelScope.launch { manager.setLowBalance(id, cents) } }
    fun dismissTip(id: String) = edit(id) { it.copy(tipDismissed = true) }
    fun remove(id: String) = viewModelScope.launch { repository.remove(id) }
}
