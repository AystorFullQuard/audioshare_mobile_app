package mme.corp.audioshare.presence

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppPresenceLifecycleObserver(
    private val lifecycleManager: PresenceLifecycleManager
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        lifecycleManager.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        lifecycleManager.onAppBackground()
    }
}
