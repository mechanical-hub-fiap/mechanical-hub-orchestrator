package com.fiap.mechanicalhub.orchestrator.domain.enums

/** Motivo informado pelo billing em `paymentFailed` (TD_paymentFailed F3). */
enum class PaymentFailureReasonEnum {
    EXPIRED,
    FAILED,
}

/** Motivo enviado no `cancelOrder` (TD_paymentFailed F4). */
enum class CancelOrderReasonEnum {
    PAYMENT_NOT_CONFIRMED,
}
