package pojo

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class AgreementPojo(
        val counterpartParty: String = "",
        val agreementID: String = "",
        val agreementCode: String = "",
        val agreementDescription: String = "",
        val agreementType: String = "",
        val agreementSubType: String = "",
        val owner: String = "",
        val counterpart: String = "",
        val tso: String = "",
        val portfolio: String = "",
        val sourceSystem: String = "",
        val programmingMethod: String = "",
        val agreementNote: String = "",
        val energy: Double = 0.0,
        val dcq: Double = 0.0,
        val price: Double = 0.0,
        val validFrom: Instant = Instant.now(),
        val validTo: Instant = validFrom.plusSeconds(86400*30)
)