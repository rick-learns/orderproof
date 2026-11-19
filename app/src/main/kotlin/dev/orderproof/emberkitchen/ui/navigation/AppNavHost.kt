package dev.orderproof.emberkitchen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.orderproof.emberkitchen.AppContainer
import dev.orderproof.emberkitchen.ui.cart.CartScreen
import dev.orderproof.emberkitchen.ui.checkout.CheckoutScreen
import dev.orderproof.emberkitchen.ui.components.LoadingState
import dev.orderproof.emberkitchen.ui.connect.ConnectScreen
import dev.orderproof.emberkitchen.ui.item.ItemScreen
import dev.orderproof.emberkitchen.ui.menu.MenuScreen
import dev.orderproof.emberkitchen.ui.order.OrderStatusScreen

object Routes {
    const val CONNECT = "connect"
    const val MENU = "menu"
    const val ITEM = "item/{itemId}"
    const val CART = "cart"
    const val CHECKOUT = "checkout"
    const val ORDER = "order/{orderId}"

    fun item(id: String) = "item/$id"
    fun order(id: String) = "order/$id"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val startupViewModel: StartupViewModel = viewModel { StartupViewModel(container) }
    val startup by startupViewModel.startup.collectAsStateWithLifecycle()
    val current = startup ?: return LoadingState("Opening Ember Kitchen…")
    val nav = rememberNavController()

    LaunchedEffect(current) {
        current.resumeRoute?.let { nav.navigate(it) }
    }

    NavHost(navController = nav, startDestination = current.startRoute) {
        composable(Routes.CONNECT) {
            ConnectScreen(container, onConnected = { nav.navigateClearing(Routes.MENU) })
        }
        composable(Routes.MENU) {
            MenuScreen(
                container = container,
                onOpenItem = { nav.navigate(Routes.item(it)) },
                onOpenCart = { nav.navigate(Routes.CART) },
                onOpenOrder = { nav.navigate(Routes.order(it)) },
                onOpenSettings = { nav.navigate(Routes.CONNECT) },
            )
        }
        composable(Routes.ITEM) { entry ->
            ItemScreen(container, itemId = entry.arguments?.getString("itemId").orEmpty(), onDone = { nav.popBackStack() })
        }
        composable(Routes.CART) {
            CartScreen(container, onBack = { nav.popBackStack() }, onReview = { nav.navigate(Routes.CHECKOUT) })
        }
        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onBackToCart = { nav.navigate(Routes.CART) { popUpTo(Routes.MENU) } },
                onConfirmed = { orderId -> nav.navigate(Routes.order(orderId)) { popUpTo(Routes.MENU) } },
            )
        }
        composable(Routes.ORDER) { entry ->
            OrderStatusScreen(
                container = container,
                orderId = entry.arguments?.getString("orderId").orEmpty(),
                onBackToMenu = { nav.navigateClearing(Routes.MENU) },
            )
        }
    }
}

private fun NavHostController.navigateClearing(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
}
