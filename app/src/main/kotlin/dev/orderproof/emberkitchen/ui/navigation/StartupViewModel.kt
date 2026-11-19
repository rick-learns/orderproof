package dev.orderproof.emberkitchen.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.core.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** [resumeRoute] is pushed on top of the menu so Back still leads somewhere sensible. */
data class Startup(val startRoute: String, val resumeRoute: String?)

/** Decides where a (re)launched process begins, and kicks off reconciliation of a pending attempt. */
class StartupViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow<Startup?>(null)
    val startup: StateFlow<Startup?> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = container.connection.current() != null
            val pending = container.attempts.load()
            if (configured && pending != null) {
                AppLog.i("startup.pending_attempt attempt=${pending.attemptId} phase=${pending.phase}")
                container.checkout.reconcilePending()
            }
            val active = if (configured && pending == null) container.activeOrder.activeOrder.first() else null
            if (active != null) AppLog.i("startup.active_order order=${active.orderId} number=${active.orderNumber}")
            mutable.value = Startup(
                startRoute = if (configured) Routes.MENU else Routes.CONNECT,
                resumeRoute = when {
                    !configured -> null
                    pending != null -> Routes.CHECKOUT
                    active != null -> Routes.order(active.orderId)
                    else -> null
                },
            )
        }
    }
}
