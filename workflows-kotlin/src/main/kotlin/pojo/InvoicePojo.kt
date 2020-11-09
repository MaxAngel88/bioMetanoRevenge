package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class InvoicePojo(
        val ownerID: String = "",
        val invoiceID: String = "",
        val invoiceRef: String = "",
        val parentQuadrioID: String = "",
        val unityPrice: Double = 0.0,
        val quantity: Double = 0.0,
        val productType: String = "",
        val invoiceStatus: String = ""
)