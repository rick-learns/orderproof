package dev.orderproof.backend.seed

import dev.orderproof.backend.domain.Coupon
import dev.orderproof.backend.domain.CouponKind
import dev.orderproof.backend.domain.MenuItem
import dev.orderproof.backend.domain.MenuOption
import dev.orderproof.backend.domain.OptionGroup

/**
 * Deterministic Ember Kitchen catalog. Every reset produces exactly this data, so verification
 * expectations (written by hand in docs/behavior-contract.md) stay stable.
 */
object SeedCatalog {
    const val RESTAURANT = "Ember Kitchen"
    const val CURRENCY = "USD"
    const val TAX_BASIS_POINTS = 825
    const val EMBER10_LIFETIME_MILLIS = 2 * 60 * 60 * 1000L

    val menu: List<MenuItem> = listOf(
        MenuItem(
            id = "ember-smash-burger",
            name = "Ember Smash Burger",
            description = "Two-day dry-aged beef, griddled hard, on a charred potato bun.",
            category = "Mains",
            basePriceMinor = 1295,
            stock = 20,
            optionGroups = listOf(
                single("burger-patty", "Patty", "patty-single" to ("Single" to 0L), "patty-double" to ("Double" to 350L)),
                OptionGroup(
                    id = "burger-addons",
                    name = "Add-ons",
                    required = false,
                    maxSelections = 3,
                    options = listOf(
                        MenuOption("addon-bacon", "Smoked bacon", 225),
                        MenuOption("addon-cheddar", "Aged cheddar", 150),
                        MenuOption("addon-jalapeno", "Charred jalapeños", 95),
                    ),
                ),
            ),
        ),
        MenuItem(
            id = "brisket-sandwich",
            name = "Oak-Smoked Brisket Sandwich",
            description = "Twelve-hour brisket, house pickles and ember-roasted garlic mayo.",
            category = "Mains",
            basePriceMinor = 1450,
            stock = 5,
            optionGroups = listOf(
                single("brisket-bread", "Bread", "bread-brioche" to ("Brioche bun" to 0L), "bread-sourdough" to ("Toasted sourdough" to 0L)),
                OptionGroup(
                    id = "brisket-extras",
                    name = "Extras",
                    required = false,
                    maxSelections = 2,
                    options = listOf(
                        MenuOption("extra-pickled-onion", "Pickled red onion", 75),
                        MenuOption("extra-brisket", "Extra brisket", 400),
                    ),
                ),
            ),
        ),
        MenuItem(
            id = "fire-roasted-cauliflower",
            name = "Fire-Roasted Cauliflower",
            description = "Whole-roasted with tahini, pomegranate and toasted seeds. Vegan.",
            category = "Mains",
            basePriceMinor = 1100,
            stock = 20,
            optionGroups = listOf(
                single("cauliflower-heat", "Heat", "heat-mild" to ("Mild" to 0L), "heat-medium" to ("Medium" to 0L), "heat-hot" to ("Hot" to 0L)),
            ),
        ),
        MenuItem(
            id = "charcoal-fries",
            name = "Charcoal-Salt Fries",
            description = "Hand-cut, twice-fried, finished with smoked salt.",
            category = "Sides",
            basePriceMinor = 595,
            stock = 40,
            optionGroups = listOf(
                single("fries-size", "Size", "fries-regular" to ("Regular" to 0L), "fries-large" to ("Large" to 250L)),
            ),
        ),
        MenuItem(
            id = "smoked-lemonade",
            name = "Smoked Honey Lemonade",
            description = "Fresh lemons, wood-smoked honey, sparkling water.",
            category = "Drinks",
            basePriceMinor = 450,
            stock = 30,
            optionGroups = listOf(
                single("lemonade-size", "Size", "lemonade-regular" to ("Regular" to 0L), "lemonade-large" to ("Large" to 150L)),
            ),
        ),
        MenuItem(
            id = "burnt-honey-cake",
            name = "Burnt Honey Cake",
            description = "Brown-butter sponge, burnt honey glaze, crème fraîche.",
            category = "Desserts",
            basePriceMinor = 795,
            stock = 8,
            optionGroups = emptyList(),
        ),
    )

    /** Coupon expiry is relative to the reset instant so it behaves the same with any clock. */
    fun coupons(resetAtMillis: Long): List<Coupon> = listOf(
        Coupon("EMBER10", CouponKind.PERCENT, value = 10, minSubtotalMinor = 2000, expiresAtMillis = resetAtMillis + EMBER10_LIFETIME_MILLIS),
        Coupon("WELCOME5", CouponKind.FIXED, value = 500, minSubtotalMinor = 1500, expiresAtMillis = null),
    )

    private fun single(id: String, name: String, vararg options: Pair<String, Pair<String, Long>>) = OptionGroup(
        id = id,
        name = name,
        required = true,
        maxSelections = 1,
        options = options.map { (optionId, nameAndPrice) -> MenuOption(optionId, nameAndPrice.first, nameAndPrice.second) },
    )
}
