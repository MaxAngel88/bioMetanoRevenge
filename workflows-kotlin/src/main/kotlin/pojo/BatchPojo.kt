package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class BatchPojo(
        val produttore: String = "",
        val shipper: String = "",
        val idProducer: String = "",
        val idShipper: String = "",
        val transactionType: String = "",
        val batchID: String = "",
        val month: String = "",
        val remiCode: String = "",
        val plantAddress: String = "",
        val plantCode: String = "",
        val initialQuantity: Double = 0.0,
        val quantity: Double = 0.0,
        val energy: Double = 0.0,
        val price: Double = 0.0,
        val averageProductionCapacity: Double = 0.0,
        val maxProductionCapacity: Double = 0.0,
        val annualEstimate: Double = 0.0,
        val pcs: Double = 0.0,
        val pci: Double = 0.0,
        val startingPosition: String = "",
        val arrivalPosition: String = "",
        val docRef: String = "",
        val docDate: String = "",
        val batchStatus: String = ""
)