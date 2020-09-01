package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ExchangePojo(
        val seller: String = "",
        val buyer: String = "",
        val exchangeType: String = "",
        val PIVASeller: String = "",
        val PIVABuyer: String = "",
        val parentBatchID: String = "",
        val exchangeCode: String = "",
        val month: String = "",
        val remiCode: String = "",
        val plantAddress: String = "",
        val plantCode: String = "",
        val hauler: String = "",
        val PIVAHauler: String = "",
        val trackCode: String = "",
        val pickupDate: String = "",
        val deliveryDate: String = "",
        val quantity: Double = 0.0,
        val price: Double = 0.0,
        val startingPosition: String = "",
        val arrivalPosition: String = "",
        val docRef: String = "",
        val docDate: String = "",
        val exchangeStatus: String = ""
)