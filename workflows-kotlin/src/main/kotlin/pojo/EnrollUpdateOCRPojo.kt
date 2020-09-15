package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class EnrollUpdateOCRPojo(
        val uuid: String = "",
        val subjectFirstName: String = "",
        val subjectLastName: String = "",
        val subjectAddress: String = "",
        val subjectBusiness: String = "",
        val businessName: String = "",
        val PIVA: String = "",
        val docRefAutodichiarazione: String = "",
        val docRefAttestazioniTecniche: String = "",
        val docDeadLineAuto: String = "",
        val docDeadLineTech: String = ""
)