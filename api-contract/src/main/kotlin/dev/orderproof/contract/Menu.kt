package dev.orderproof.contract

import kotlinx.serialization.Serializable

/** Prices are always integer minor units (cents) of [MenuResponse.currency]. */
@Serializable
data class MenuResponse(
    val restaurant: String,
    val currency: String,
    val items: List<MenuItemDto>,
)

@Serializable
data class MenuItemDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val basePriceMinor: Long,
    val available: Boolean,
    val optionGroups: List<OptionGroupDto>,
)

@Serializable
data class OptionGroupDto(
    val id: String,
    val name: String,
    /** When true exactly one option of this group must be selected. */
    val required: Boolean,
    val maxSelections: Int,
    val options: List<OptionDto>,
)

@Serializable
data class OptionDto(
    val id: String,
    val name: String,
    val priceDeltaMinor: Long,
)
