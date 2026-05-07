package com.example.myfirstapp.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry

@Composable
fun <T : Any> ImmediateNavDisplay(
    entries: List<NavEntry<T>>,
    activeContentKey: Any,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    require(entries.isNotEmpty()) { "ImmediateNavDisplay entries cannot be empty" }

    BackHandler(onBack = onBack)
    Box(modifier = modifier) {
        val bottomSheetScene = calculateBottomSheetScene(entries = entries, onBack = onBack)
        val baseEntries = bottomSheetScene?.overlaidEntries ?: entries
        val activeEntry = baseEntries.lastOrNull { entry -> entry.contentKey == activeContentKey }
            ?: baseEntries.last()

        baseEntries.forEach { entry ->
            val isActive = entry.contentKey == activeEntry.contentKey
            val maxLifecycle = if (isActive) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
            Box(
                modifier = if (isActive) {
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                } else {
                    Modifier
                        .fillMaxSize()
                        .alpha(0f)
                        .zIndex(0f)
                        .clearAndSetSemantics {}
                }
            ) {
                key(entry.contentKey) {
                    val lifecycleOwner = rememberEntryLifecycleOwner(maxLifecycle = maxLifecycle)
                    CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                        entry.Content()
                    }
                }
            }
        }

        if (bottomSheetScene != null) {
            key(bottomSheetScene.key) {
                bottomSheetScene.content()
            }
        }
    }
}

@Composable
private fun rememberEntryLifecycleOwner(maxLifecycle: Lifecycle.State): LifecycleOwner {
    val parentLifecycleOwner = LocalLifecycleOwner.current
    val owner = remember(parentLifecycleOwner) {
        EntryLifecycleOwner(parentLifecycleOwner.lifecycle.currentState)
    }
    DisposableEffect(owner, parentLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            owner.parentLifecycleState = event.targetState
        }
        parentLifecycleOwner.lifecycle.addObserver(observer)
        owner.parentLifecycleState = parentLifecycleOwner.lifecycle.currentState
        onDispose {
            parentLifecycleOwner.lifecycle.removeObserver(observer)
            owner.destroy()
        }
    }
    SideEffect {
        owner.maxLifecycleState = maxLifecycle
        owner.parentLifecycleState = parentLifecycleOwner.lifecycle.currentState
    }
    return owner
}

private class EntryLifecycleOwner(
    initialParentState: Lifecycle.State
) : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    var parentLifecycleState: Lifecycle.State = initialParentState
        set(value) {
            field = value
            updateLifecycleState()
        }

    var maxLifecycleState: Lifecycle.State = Lifecycle.State.INITIALIZED
        set(value) {
            field = value
            updateLifecycleState()
        }

    fun destroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun updateLifecycleState() {
        val targetState = when {
            parentLifecycleState == Lifecycle.State.DESTROYED -> Lifecycle.State.DESTROYED
            parentLifecycleState.ordinal < maxLifecycleState.ordinal -> parentLifecycleState
            else -> maxLifecycleState
        }
        if (
            lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED &&
            targetState == Lifecycle.State.DESTROYED
        ) {
            return
        }
        lifecycleRegistry.currentState = targetState
    }
}
