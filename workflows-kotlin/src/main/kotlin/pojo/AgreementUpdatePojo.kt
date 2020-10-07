package pojo

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class AgreementUpdatePojo(
        val agreementID: String = "",
        val energy: Double = 0.0,
        val dcq: Double = 0.0,
        val price: Double = 0.0,
        val validFrom: Instant = Instant.now(),
        val validTo: Instant = validFrom.plusSeconds(86400*30)
)