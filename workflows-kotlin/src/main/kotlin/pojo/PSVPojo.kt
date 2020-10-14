package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PSVPojo(
        val seller: String = "",
        val buyer: String = "",
        val transactionType: String = "",
        val PIVASeller: String = "",
        val PIVABuyer: String = "",
        val parentBatchID: String = "",
        val transactionCode: String = "",
        val month: String = "",
        val remiCode: String = "",
        val plantAddress: String = "",
        val plantCode: String = "",
        val initialQuantity: Double = 0.0,
        val quantity: Double = 0.0,
        val price: Double = 0.0,
        val pcs: Double = 0.0,
        val pci: Double = 0.0,
        val docRef: String = "",
        val docDate: String = "",
        val transactionStatus: String = "",
        val supportField: String = "",
        val snamChek: String = "toCheck",
        val auctionStatus: String = ""
)