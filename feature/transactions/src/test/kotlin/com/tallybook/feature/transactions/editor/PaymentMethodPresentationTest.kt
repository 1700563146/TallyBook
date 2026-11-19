package com.tallybook.feature.transactions.editor

import com.tallybook.core.model.transaction.AccountType
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMethodPresentationTest {

    @Test
    fun common_payment_methods_are_ordered_by_everyday_use() {
        val ranks = listOf(
            "微信支付" to AccountType.E_WALLET,
            "支付宝" to AccountType.E_WALLET,
            "招商银行" to AccountType.DEBIT_CARD,
            "现金" to AccountType.CASH,
        ).map { (name, type) -> paymentMethodRank(name, type) }

        assertTrue(ranks.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun custom_wallet_stays_available_after_primary_methods() {
        assertTrue(
            paymentMethodRank("美团钱包", AccountType.E_WALLET) <
                paymentMethodRank("其他账户", AccountType.OTHER),
        )
    }
}
