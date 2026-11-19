package dev.orderproof.backend.control

import dev.orderproof.backend.storage.Database
import dev.orderproof.backend.storage.IdempotencyRepository
import dev.orderproof.backend.storage.MenuRepository
import dev.orderproof.backend.storage.OrderRepository
import dev.orderproof.backend.storage.PaymentRepository
import dev.orderproof.control.LedgerIdempotencyRecord
import dev.orderproof.control.LedgerLine
import dev.orderproof.control.LedgerOrder
import dev.orderproof.control.LedgerPayment
import dev.orderproof.control.LedgerSnapshot
import dev.orderproof.control.StockLevel

/** Dumps persisted rows verbatim. No business logic: the verifier must judge them independently. */
class LedgerReader(private val db: Database, private val nowMillis: () -> Long) {
    fun snapshot(): LedgerSnapshot = db.transaction { conn ->
        LedgerSnapshot(
            orders = OrderRepository.all(conn).map { o ->
                LedgerOrder(
                    orderId = o.orderId,
                    orderNumber = o.orderNumber,
                    idempotencyKey = o.idempotencyKey,
                    checkoutAttemptId = o.checkoutAttemptId,
                    status = o.status.name,
                    lines = o.lines.map { LedgerLine(it.itemId, it.optionIds, it.quantity, it.unitPriceMinor, it.lineTotalMinor) },
                    subtotalMinor = o.subtotalMinor,
                    discountMinor = o.discountMinor,
                    taxMinor = o.taxMinor,
                    totalMinor = o.totalMinor,
                    couponCode = o.couponCode,
                    createdAtMillis = o.createdAtMillis,
                )
            },
            payments = PaymentRepository.all(conn).map {
                LedgerPayment(it.paymentId, it.orderId, it.amountMinor, it.status, it.method, it.createdAtMillis)
            },
            idempotencyRecords = IdempotencyRepository.all(conn).map {
                LedgerIdempotencyRecord(it.idempotencyKey, it.orderId, it.requestHash, it.replayCount)
            },
            stock = MenuRepository.load(conn).map { StockLevel(it.id, it.stock) },
            clockEpochMillis = nowMillis(),
        )
    }
}
