package dev.orderproof.emberkitchen

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.orderproof.emberkitchen.candidate.CandidateBuild
import dev.orderproof.emberkitchen.core.AppLog
import dev.orderproof.emberkitchen.data.menu.MenuRepository
import dev.orderproof.emberkitchen.data.net.EmberApi
import dev.orderproof.emberkitchen.data.net.HttpClients
import dev.orderproof.emberkitchen.data.settings.ConnectionSettings
import dev.orderproof.emberkitchen.data.store.DataStoreActiveOrderStore
import dev.orderproof.emberkitchen.data.store.DataStoreAttemptStore
import dev.orderproof.emberkitchen.data.store.DataStoreCartStore
import dev.orderproof.emberkitchen.domain.checkout.CheckoutCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.orderProofStore by preferencesDataStore(name = "orderproof")

/** Manual dependency graph; one instance per process. */
class AppContainer(context: Context) {
    private val dataStore = context.applicationContext.orderProofStore
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val connection = ConnectionSettings(dataStore)
    val api = EmberApi(HttpClients.create(AppLog::i), connection::current)
    val menu = MenuRepository(api)
    val cart = DataStoreCartStore(dataStore)
    val attempts = DataStoreAttemptStore(dataStore)
    val activeOrder = DataStoreActiveOrderStore(dataStore)
    val checkout = CheckoutCoordinator(
        gateway = api,
        attempts = attempts,
        cart = cart,
        activeOrder = activeOrder,
        keys = CandidateBuild.checkoutKeys,
        scope = appScope,
        log = AppLog::i,
    )
}
