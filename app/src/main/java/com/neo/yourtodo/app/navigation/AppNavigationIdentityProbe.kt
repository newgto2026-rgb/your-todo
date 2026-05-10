package com.neo.yourtodo.app.navigation

import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import java.util.Collections

object AppNavigationIdentityProbe {
    data class EntryEvent(
        val contentKey: String,
        val identityHash: Int
    )

    data class ViewModelEvent(
        val contentKey: String,
        val viewModelIdentityHashes: List<Int>
    )

    @Volatile
    private var enabled = false

    private val entryEvents = Collections.synchronizedList(mutableListOf<EntryEvent>())
    private val viewModelEvents = Collections.synchronizedList(mutableListOf<ViewModelEvent>())

    fun start() {
        clear()
        enabled = true
    }

    fun stop() {
        enabled = false
        clear()
    }

    fun snapshotEntryEvents(): List<EntryEvent> =
        synchronized(entryEvents) { entryEvents.toList() }

    fun snapshotViewModelEvents(): List<ViewModelEvent> =
        synchronized(viewModelEvents) { viewModelEvents.toList() }

    internal fun publishEntries(entries: List<NavEntry<NavKey>>) {
        if (!enabled) return
        entries.forEach { entry ->
            entryEvents.add(
                EntryEvent(
                    contentKey = entry.contentKey.toString(),
                    identityHash = System.identityHashCode(entry)
                )
            )
        }
    }

    internal fun publishViewModels(contentKey: Any, viewModelStore: ViewModelStore) {
        if (!enabled) return
        val hashes = viewModelStore.viewModelIdentityHashes()
        if (hashes.isEmpty()) return
        viewModelEvents.add(
            ViewModelEvent(
                contentKey = contentKey.toString(),
                viewModelIdentityHashes = hashes
            )
        )
    }

    private fun clear() {
        entryEvents.clear()
        viewModelEvents.clear()
    }

    private fun ViewModelStore.viewModelIdentityHashes(): List<Int> =
        runCatching {
            val field = ViewModelStore::class.java.declaredFields
                .firstOrNull { Map::class.java.isAssignableFrom(it.type) }
            if (field == null) {
                emptyList()
            } else {
                field.isAccessible = true
                val viewModels = field.get(this) as? Map<*, *>
                viewModels.orEmpty().values
                    .mapNotNull { viewModel -> viewModel?.let(System::identityHashCode) }
                    .sorted()
            }
        }.getOrDefault(emptyList())
}
