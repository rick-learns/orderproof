package dev.orderproof.emberkitchen.data.menu

import dev.orderproof.contract.MenuResponse
import dev.orderproof.emberkitchen.data.net.EmberApi
import dev.orderproof.emberkitchen.domain.ApiOutcome

/** Keeps the last menu in memory so the item screen opens instantly; availability comes from the server. */
class MenuRepository(private val api: EmberApi) {
    @Volatile
    private var last: MenuResponse? = null

    fun cached(): MenuResponse? = last

    suspend fun load(): ApiOutcome<MenuResponse> = api.menu().also { if (it is ApiOutcome.Success) last = it.value }
}
