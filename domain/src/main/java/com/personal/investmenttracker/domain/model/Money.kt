package com.personal.investmenttracker.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money(
    val amount: BigDecimal,
) : Comparable<Money> {
    init {
        require(!amount.stripTrailingZeros().toString().contains("NaN")) {
            "Amount cannot be NaN"
        }
    }

    operator fun plus(other: Money): Money = Money(this.amount.add(other.amount))

    operator fun minus(other: Money): Money = Money(this.amount.subtract(other.amount))

    operator fun times(factor: BigDecimal): Money = Money(this.amount.multiply(factor))

    operator fun times(qty: Quantity): Money = Money(this.amount.multiply(qty.value))

    operator fun unaryMinus(): Money = Money(this.amount.negate())

    override fun compareTo(other: Money): Int = this.amount.compareTo(other.amount)

    fun toDisplayString(scale: Int = DISPLAY_SCALE): String =
        this.amount
            .setScale(scale, RoundingMode.HALF_UP)
            .toPlainString()

    companion object {
        const val DISPLAY_SCALE = 2
        val ZERO = Money(BigDecimal.ZERO)

        fun of(value: String): Money = Money(BigDecimal(value))

        fun of(value: Long): Money = Money(BigDecimal.valueOf(value))
    }
}

@JvmInline
value class Quantity(
    val value: BigDecimal,
) : Comparable<Quantity> {
    operator fun plus(other: Quantity): Quantity = Quantity(this.value.add(other.value))

    operator fun minus(other: Quantity): Quantity = Quantity(this.value.subtract(other.value))

    operator fun times(price: Money): Money = Money(this.value.multiply(price.amount))

    override fun compareTo(other: Quantity): Int = this.value.compareTo(other.value)

    val isZero: Boolean
        get() = this.value.compareTo(BigDecimal.ZERO) == 0

    val isNegative: Boolean
        get() = this.value.compareTo(BigDecimal.ZERO) < 0

    val isPositive: Boolean
        get() = this.value.compareTo(BigDecimal.ZERO) > 0

    fun toDisplayString(scale: Int = 0): String =
        this.value
            .setScale(scale, RoundingMode.HALF_UP)
            .toPlainString()

    companion object {
        val ZERO = Quantity(BigDecimal.ZERO)

        fun of(value: String): Quantity = Quantity(BigDecimal(value))

        fun of(value: Long): Quantity = Quantity(BigDecimal.valueOf(value))
    }
}
