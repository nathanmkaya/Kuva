package dev.nathanmkaya.kuva.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The lifecycle event to start the camera on. */
enum class LifecycleStart {
    /** Start the camera when the lifecycle enters the `ON_START` state. */
    OnStart,

    /** Start the camera when the lifecycle enters the `ON_RESUME` state. */
    OnResume,
}

/** The lifecycle event to stop the camera on. */
enum class LifecycleStop {
    /** Stop the camera when the lifecycle enters the `ON_STOP` state. */
    OnStop,

    /** Stop the camera when the lifecycle enters the `ON_PAUSE` state. */
    OnPause,
}

/**
 * Binds the camera [Controller] to a [Lifecycle].
 *
 * @param lifecycle The lifecycle to bind to.
 * @param scope The coroutine scope to use for starting and stopping the camera.
 * @param startOn The lifecycle event to start the camera on. Defaults to [LifecycleStart.OnStart].
 * @param stopOn The lifecycle event to stop the camera on. Defaults to [LifecycleStop.OnStop].
 * @return An [AutoCloseable] that can be used to unbind the controller from the lifecycle.
 */
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
