package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class InvoiceUpdateStatusPojo(
        val invoiceID: String = "",
        val invoiceStatus: String = ""
)