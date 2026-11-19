package dev.orderproof.backend

import dev.orderproof.backend.config.BackendConfig
import dev.orderproof.backend.config.ClockMode
import dev.orderproof.contract.OrderLineRequest
import dev.orderproof.contract.PlaceOrderRequest
import java.nio.file.Files
import java.nio.file.Path

const val TEST_TOKEN = "test-control-token"

fun tempDb(): Path = Files.createTempDirectory("orderproof-test").resolve("orderproof.db")

fun startTestBackend(db: Path = tempDb(), withControl: Boolean = true): BackendApp = BackendApp(
    BackendConfig(publicHost = "127.0.0.1", publicPort = 0, controlPort = 0, databaseFile = db, clockMode = ClockMode.FIXED),
    controlToken = if (withControl) TEST_TOKEN else null,
).start()

/** 1 × Ember Smash Burger (single) = 1295; tax 107 (106.84); total 1402. */
val singleBurger = OrderLineRequest("ember-smash-burger", listOf("patty-single"), 1)

fun singleBurgerOrder(expectedTotal: Long = 1402) =
    PlaceOrderRequest(listOf(singleBurger), couponCode = null, expectedTotalMinor = expectedTotal, paymentToken = "sim_visa_4242")
