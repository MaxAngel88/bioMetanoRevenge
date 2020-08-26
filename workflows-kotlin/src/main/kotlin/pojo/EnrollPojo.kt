package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class EnrollPojo(
        val enrollType: String = "",
        val businessName: String = "",
        val PIVA: String = "",
        val birthPlace: String = "",
        val idPlant: String = "",
        val plantAddress: String = "",
        val username: String = "",
        val role: String = "",
        val partner: String = "",
        val docRefAutodichiarazione: String = "",
        val docRefAttestazioniTecniche: String = "",
        val docDeadLine: String = "",
        val enrollStatus: String = "",
        val bioGasAmount: Double = 0.0,
        val gasAmount: Double = 0.0,
        val uuid: String = ""
)