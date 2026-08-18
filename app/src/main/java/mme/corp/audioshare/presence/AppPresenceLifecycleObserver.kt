package mme.corp.audioshare.presence

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import mme.corp.audioshare.runtime.SessionRuntimeReconciler

class AppPresenceLifecycleObserver(
    private val lifecycleManager: PresenceLifecycleManager,
    private val sessionRuntimeReconciler: SessionRuntimeReconciler
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        val runtimeReady = lifecycleManager.onAppForeground()
        sessionRuntimeReconciler.onAppForeground(runtimeReady)
    }

    override fun onStop(owner: LifecycleOwner) {
        sessionRuntimeReconciler.onAppBackground()
        lifecycleManager.onAppBackground()
    }
}
