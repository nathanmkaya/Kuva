package dev.nathanmkaya.kuva.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class LifecycleStart {
    OnStart,
    OnResume,
}

enum class LifecycleStop {
    OnStop,
    OnPause,
}

fun Controller.bindTo(
    lifecycle: Lifecycle,
    scope: CoroutineScope,
    startOn: LifecycleStart = LifecycleStart.OnStart,
    stopOn: LifecycleStop = LifecycleStop.OnStop,
): AutoCloseable {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START ->
                if (startOn == LifecycleStart.OnStart) scope.launch { runCatching { start() } }
            Lifecycle.Event.ON_RESUME ->
                if (startOn == LifecycleStart.OnResume) scope.launch { runCatching { start() } }
            Lifecycle.Event.ON_PAUSE ->
                if (stopOn == LifecycleStop.OnPause) scope.launch { runCatching { stop() } }
            Lifecycle.Event.ON_STOP ->
                if (stopOn == LifecycleStop.OnStop) scope.launch { runCatching { stop() } }
            else -> Unit
        }
    }
    lifecycle.addObserver(observer)
    return AutoCloseable {
        lifecycle.removeObserver(observer)
        scope.launch { runCatching { stop() } }
    }
}
