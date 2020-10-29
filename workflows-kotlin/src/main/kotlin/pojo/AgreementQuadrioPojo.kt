package pojo

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class AgreementQuadrioPojo(
        val counterpartParty: String = "",
        val agreementQuadrioID: String = "",
        val agreementQuadrioCode: String = "",
        val agreementQuadrioType: String = "",
        val owner: String = "",
        val counterpart: String = "",
        val remiCode: String = "",
        val yearRef: String = "",
        val supportField: String = "",
        val yearQuantity: Double = 0.0,
        val validFrom: Instant = Instant.now(),
        val validTo: Instant = validFrom.plusSeconds(86400*30),
        val agreementQuadrioStatus: String = ""
)