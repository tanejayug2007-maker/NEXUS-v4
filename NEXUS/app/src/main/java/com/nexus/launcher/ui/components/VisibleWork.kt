package com.nexus.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/*
 * Timer loops ("refresh every N seconds") must not keep running while the screen that shows them is
 * stopped. The launcher stays alive all day as the default Home app, so a plain `while (true) delay()`
 * in a composable keeps waking the CPU while you use other apps. These helpers run the loop only while
 * the host (activity, dream, …) is at least STARTED, and restart it — with a fresh value — on return.
 * Frame-clock animations (withFrameNanos) already pause by themselves; this is for delay()-based work.
 */

/** [produceState] whose producer only runs while the host lifecycle is at least STARTED. */
@Composable
fun <T> produceWhileVisible(initial: T, vararg keys: Any?, producer: suspend ProduceStateScope<T>.() -> Unit): State<T> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initial, lifecycle, *keys) {
        val scope = this
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { scope.producer() }
    }
}

/** [LaunchedEffect] whose block only runs while the host lifecycle is at least STARTED. */
@Composable
fun WhileVisibleEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, *keys) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}
