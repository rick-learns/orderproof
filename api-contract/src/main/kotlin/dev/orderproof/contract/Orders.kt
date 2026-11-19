package dev.orderproof.contract

import kotlinx.serialization.Serializable

@Serializable
enum class OrderStatus { PLACED, PREPARING, READY, COMPLETED }

@Serializable
data class OrderDto(
    val orderId: String,
    val orderNumber: String,
    val status: OrderStatus,
    val lines: List<QuotedLine>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val couponCode: String? = null,
    val paymentStatus: String,
    val placedAt: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
)
