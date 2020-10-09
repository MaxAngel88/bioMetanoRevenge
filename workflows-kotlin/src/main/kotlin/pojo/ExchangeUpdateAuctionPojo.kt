package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ExchangeUpdateAuctionPojo(
        val exchangeCode: String = "",
        val supportField: String = "",
        val auctionStatus: String =""
)