package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class EnrollPojo(
        val enrollType: String = "",
        val subjectFirstName: String = "",
        val subjectLastName: String = "",
        val subjectAddress: String = "",
        val subjectBusiness: String = "",
        val qualificationCode: String = "",
        val businessName: String = "",
        val PIVA: String = "",
        val birthPlace: String = "",
        val remiCode: String = "",
        val remiAddress: String = "",
        val idPlant: String = "",
        val plantAddress: String = "",
        val username: String = "",
        val role: String = "",
        val partner: String = "",
        val docRefAutodichiarazione: String = "",
        val docRefAttestazioniTecniche: String = "",
        val docDeadLineAuto: String = "",
        val docDeadLineTech: String = "",
        val enrollStatus: String = "",
        val bioGasAmount: Double = 0.0,
        val gasAmount: Double = 0.0,
        val uuid: String = ""
)