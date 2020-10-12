package com.bioMetanoRevenge.server

import com.bioMetanoRevenge.flow.AgreementFlow.IssuerAgreement
import com.bioMetanoRevenge.flow.AgreementFlow.UpdaterAgreement
import com.bioMetanoRevenge.flow.AgreementFlow.UpdaterAgreementStatus
import com.bioMetanoRevenge.flow.BatchFlow.IssuerBatch
import com.bioMetanoRevenge.flow.BatchFlow.UpdaterBatch
import com.bioMetanoRevenge.flow.BatchFlow.UpdaterBatchAuction
import com.bioMetanoRevenge.flow.EnrollFlow.IssuerEnroll
import com.bioMetanoRevenge.flow.EnrollFlow.UpdaterEnroll
import com.bioMetanoRevenge.flow.EnrollFlow.UpdaterOCREnroll
import com.bioMetanoRevenge.flow.ExchangeFlow.IssuerExchange
import com.bioMetanoRevenge.flow.ExchangeFlow.UpdaterExchange
import com.bioMetanoRevenge.flow.ExchangeFlow.UpdaterExchangeAuction
import com.bioMetanoRevenge.flow.PSVFlow.IssuerPSVState
import com.bioMetanoRevenge.flow.PSVFlow.UpdaterPSVState
import com.bioMetanoRevenge.flow.PSVFlow.UpdaterPSVStateAuction
import com.bioMetanoRevenge.flow.ProgrammingFlow.IssuerProgramming
import com.bioMetanoRevenge.flow.ProgrammingFlow.UpdaterProgrammingDoc
import com.bioMetanoRevenge.flow.ProgrammingFlow.UpdaterProgrammingStatus
import com.bioMetanoRevenge.flow.RawMaterialFlow.IssuerRawMaterial
import com.bioMetanoRevenge.flow.ReportFlow.IssuerReport
import com.bioMetanoRevenge.flow.ReportFlow.UpdaterReportState
import com.bioMetanoRevenge.flow.WalletRewardFlow.IssuerWalletReward
import com.bioMetanoRevenge.flow.WalletRewardFlow.UpdaterWalletReward
import com.bioMetanoRevenge.schema.*
import com.bioMetanoRevenge.state.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import pojo.*

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@CrossOrigin
@RequestMapping("/api/bioMetanoRevenge/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [ APPLICATION_JSON_VALUE ])
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     *
     * ENROLLMENT API **********************************************************************************************
     *
     */

    /**
     * Displays all EnrollStates that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastEnrollments" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastEnrollments() : ResponseEntity<ResponsePojo> {
        var foundLastEnrollStates = proxy.vaultQueryBy<EnrollState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Enrolled list", data = foundLastEnrollStates))
    }

    /**
     * Displays last EnrollStates that exist in the node's vault for selected username.
     */
    @GetMapping(value = [ "getLastEnrollByUsername/{username}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastEnrollByUsername(
            @PathVariable("username")
            username : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for username
        var usernameCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {EnrollSchemaV1.PersistentEnroll::username.equal(username)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(EnrollState::class.java))

        val foundUsernameEnroll = proxy.vaultQueryBy<EnrollState>(
                usernameCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Enrolled by username $username .", data = foundUsernameEnroll))
    }

    /**
     * Displays History EnrollStates that exist in the node's vault for selected username.
     */
    @GetMapping(value = [ "getHistoryEnrollStateByUsername/{username}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryEnrollStateByUsername(
            @PathVariable("username")
            username : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for username
        var usernameCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {EnrollSchemaV1.PersistentEnroll::username.equal(username)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(EnrollState::class.java))


        val foundUsernameEnrollHistory = proxy.vaultQueryBy<EnrollState>(
                usernameCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of enroll state for $username", data = foundUsernameEnrollHistory))
    }

    /**
     * Displays last EnrollStates that exist in the node's vault for selected uuid.
     */
    @GetMapping(value = [ "getLastEnrollByUuid/{uuid}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastEnrollByUuid(
            @PathVariable("uuid")
            uuid : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for uuid
        var uuidCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {EnrollSchemaV1.PersistentEnroll::uuid.equal(uuid)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(EnrollState::class.java))

        val foundUuidEnroll = proxy.vaultQueryBy<EnrollState>(
                uuidCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Enrolled by uuid $uuid .", data = foundUuidEnroll))
    }

    /**
     * Displays History EnrollStates that exist in the node's vault for selected uuid.
     */
    @GetMapping(value = [ "getHistoryEnrollStateByUuid/{uuid}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryEnrollStateByUuid(
            @PathVariable("uuid")
            uuid : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for uuid
        var uuidCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {EnrollSchemaV1.PersistentEnroll::uuid.equal(uuid)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(EnrollState::class.java))

        val foundUuidEnrollHistory = proxy.vaultQueryBy<EnrollState>(
                uuidCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of enroll state for $uuid", data = foundUuidEnrollHistory))
    }

    /**
     * Displays last EnrollStates that exist in the node's vault for selected qualificationCode.
     */
    @GetMapping(value = [ "getLastEnrollByQualification/{qualificationCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastEnrollByQualification(
            @PathVariable("qualificationCode")
            qualificationCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for qualificationCode
        var qualificationCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {EnrollSchemaV1.PersistentEnroll::qualificationCode.equal(qualificationCode)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(EnrollState::class.java))

        val foundQualificationCodeEnroll = proxy.vaultQueryBy<EnrollState>(
                qualificationCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Enrolled by qualificationCode $qualificationCode .", data = foundQualificationCodeEnroll))
    }

    /**
     * Displays History EnrollStates that exist in the node's vault for selected qualificationCode.
     */
    @GetMapping(value = [ "getHistoryEnrollStateByQualification/{qualificationCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryEnrollStateByQualification(
            @PathVariable("qualificationCode")
            qualificationCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for qualificationCode
        var qualificationCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {EnrollSchemaV1.PersistentEnroll::qualificationCode.equal(qualificationCode)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(EnrollState::class.java))

        val foundQualificationEnrollHistory = proxy.vaultQueryBy<EnrollState>(
                qualificationCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of enroll state for $qualificationCode", data = foundQualificationEnrollHistory))
    }

    /**
     * Initiates a flow to agree an Enroll between two nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-enroll" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueEnroll(
            @RequestBody
            issueEnrollPojo : EnrollPojo): ResponseEntity<ResponsePojo> {

        val uuid = issueEnrollPojo.uuid

        if(uuid.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "uuid cannot be empty", data = null))
        }

        return try {
            val enroll = proxy.startTrackedFlow(::IssuerEnroll, issueEnrollPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${enroll.linearId.id} committed to ledger.\n", data = enroll))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }


    /***
     *
     * Update Enroll
     *
     */
    @PostMapping(value = [ "update-enroll" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateEnroll(
            @RequestBody
            updateEnrollPojo: EnrollUpdatePojo): ResponseEntity<ResponsePojo> {

        val uuid = updateEnrollPojo.uuid
        val enrollStatus = updateEnrollPojo.enrollStatus
        val bioGasAmount = updateEnrollPojo.bioGasAmount
        val gasAmount = updateEnrollPojo.gasAmount

        if(uuid.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "uuid cannot be empty", data = null))
        }

        if(enrollStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "enrollStatus cannot be empty", data = null))
        }

        if(bioGasAmount.isNaN() || bioGasAmount < 0.0) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "bioGasAmount must be a >= 0.0 number", data = null))
        }

        if(gasAmount.isNaN() || bioGasAmount < 0.0) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "gasAmount must be a >= 0.0 number", data = null))
        }

        return try {
            val updateEnroll = proxy.startTrackedFlow(::UpdaterEnroll, updateEnrollPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Enroll with id: $uuid update correctly. New EnrollState with id: ${updateEnroll.linearId.id} created.. ledger updated.", data = updateEnroll))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }


    /***
     *
     * Update OCR Enroll
     *
     */
    @PostMapping(value = [ "update-ocr-enroll" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateOCREnroll(
            @RequestBody
            updateOCREnrollPojo: EnrollUpdateOCRPojo): ResponseEntity<ResponsePojo> {

        val uuid = updateOCREnrollPojo.uuid
        val subjectFirstName = updateOCREnrollPojo.subjectFirstName
        val subjectLastName = updateOCREnrollPojo.subjectLastName
        val subjectAddress = updateOCREnrollPojo.subjectAddress
        val subjectBusiness = updateOCREnrollPojo.subjectBusiness
        val businessName = updateOCREnrollPojo.businessName
        val PIVA = updateOCREnrollPojo.PIVA
        val idPlant = updateOCREnrollPojo.idPlant
        val plantAddress = updateOCREnrollPojo.plantAddress
        val remiCode = updateOCREnrollPojo.remiCode
        val docRefAutodichiarazione = updateOCREnrollPojo.docRefAutodichiarazione
        val docRefAttestazioniTecniche = updateOCREnrollPojo.docRefAttestazioniTecniche

        if(uuid.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "uuid cannot be empty", data = null))
        }

        if(subjectFirstName.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "subjectFirstName cannot be empty", data = null))
        }

        if(subjectLastName.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "subjectLastName cannot be empty", data = null))
        }

        if(subjectAddress.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "subjectAddress cannot be empty", data = null))
        }

        if(subjectBusiness.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "subjectBusiness cannot be empty", data = null))
        }

        if(businessName.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "businessName cannot be empty", data = null))
        }

        if(PIVA.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "PIVA cannot be empty", data = null))
        }

        if(docRefAutodichiarazione.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "docRefAutodichiarazione cannot be empty", data = null))
        }

        if(docRefAttestazioniTecniche.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "docRefAttestazioniTecniche cannot be empty", data = null))
        }

        if(idPlant.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "idPlant cannot be empty", data = null))
        }

        if(plantAddress.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "plantAddress cannot be empty", data = null))
        }

        if(remiCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "remiCode cannot be empty", data = null))
        }

        return try {
            val updateOCREnroll = proxy.startTrackedFlow(::UpdaterOCREnroll, updateOCREnrollPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Enroll with id: $uuid update OCR correctly. New EnrollState with id: ${updateOCREnroll.linearId.id} created.. ledger updated.", data = updateOCREnroll))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * PROGRAMMING API **********************************************************************************************
     *
     */

    /**
     * Displays all ProgrammingStates that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastProgramming" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastProgramming() : ResponseEntity<ResponsePojo> {
        var foundLastProgrammingStates = proxy.vaultQueryBy<ProgrammingState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Programming list", data = foundLastProgrammingStates))
    }

    /**
     * Displays last ProgrammingStates that exist in the node's vault for selected bioAgreementCode.
     */
    @GetMapping(value = [ "getLastProgrammingByBioAgreementCode/{bioAgreementCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastProgrammingByBioAgreementCode(
            @PathVariable("bioAgreementCode")
            bioAgreementCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for bioAgreementCode
        var bioAgreementCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ProgrammingSchemaV1.PersistentProgramming::bioAgreementCode.equal(bioAgreementCode)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ProgrammingState::class.java))

        val foundBioAgreementProgramming = proxy.vaultQueryBy<ProgrammingState>(
                bioAgreementCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Programming by bioAgreementCode $bioAgreementCode .", data = foundBioAgreementProgramming))
    }

    /**
     * Displays History ProgrammingStates that exist in the node's vault for selected bioAgreementCode.
     */
    @GetMapping(value = [ "getHistoryProgrammingByBioAgreementCode/{bioAgreementCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryProgrammingByBioAgreementCode(
            @PathVariable("bioAgreementCode")
            bioAgreementCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for bioAgreementCode
        var bioAgreementCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ProgrammingSchemaV1.PersistentProgramming::bioAgreementCode.equal(bioAgreementCode)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ProgrammingState::class.java))


        val foundBioAgreementCodeProgrammingHistory = proxy.vaultQueryBy<ProgrammingState>(
                bioAgreementCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of programming state for $bioAgreementCode", data = foundBioAgreementCodeProgrammingHistory))
    }

    /**
     * Displays last ProgrammingStates that exist in the node's vault for selected programmingStatus.
     */
    @GetMapping(value = [ "getLastProgrammingByStatus/{programmingStatus}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastProgrammingByStatus(
            @PathVariable("programmingStatus")
            programmingStatus : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for programmingStatus
        var programmingStatusCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ProgrammingSchemaV1.PersistentProgramming::programmingStatus.equal(programmingStatus)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ProgrammingState::class.java))

        val foundProgrammingStatusProgramming = proxy.vaultQueryBy<ProgrammingState>(
                programmingStatusCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Programming by programmingStatus $programmingStatus .", data = foundProgrammingStatusProgramming))
    }

    /**
     * Displays History ProgrammingStates that exist in the node's vault for selected programmingStatus.
     */
    @GetMapping(value = [ "getHistoryProgrammingByStatus/{programmingStatus}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryProgrammingByStatus(
            @PathVariable("programmingStatus")
            programmingStatus : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for programmingStatus
        var programmingStatusCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ProgrammingSchemaV1.PersistentProgramming::programmingStatus.equal(programmingStatus)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ProgrammingState::class.java))


        val foundProgrammingStatusProgrammingHistory = proxy.vaultQueryBy<ProgrammingState>(
                programmingStatusCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of programming state for $programmingStatus", data = foundProgrammingStatusProgrammingHistory))
    }

    /**
     * Displays last ProgrammingState that exist in the node's vault for selected produttore (organization name).
     */
    @GetMapping(value = [ "getLastProgrammingByProducer/{producerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastProgrammingByProducer(
            @PathVariable("producerName")
            producerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for producer organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ProgrammingState::class.java))

        val foundProducerProgramming = proxy.vaultQueryBy<ProgrammingState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.produttore.name.organisation == producerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Programming by produttore $producerName .", data = foundProducerProgramming))
    }

    /**
     * Displays History ProgrammingState that exist in the node's vault for selected produttore (organization name).
     */
    @GetMapping(value = [ "getHistoryProgrammingByProducer/{producerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryProgrammingByProducer(
            @PathVariable("producerName")
            producerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for producer organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(ProgrammingState::class.java))

        val foundProducerProgrammingHistory = proxy.vaultQueryBy<ProgrammingState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.produttore.name.organisation == producerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of programming state for $producerName", data = foundProducerProgrammingHistory))
    }

    /**
     * Initiates a flow to agree an Programming between two nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-programming" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueProgramming(
            @RequestBody
            issueProgrammingPojo : ProgrammingPojo): ResponseEntity<ResponsePojo> {

        val bioAgreementCode = issueProgrammingPojo.bioAgreementCode
        val versionFile = issueProgrammingPojo.versionFile
        val docRef = issueProgrammingPojo.docRef
        val docName = issueProgrammingPojo.docName

        if(bioAgreementCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "bioAgreementCode cannot be empty", data = null))
        }

        if(versionFile.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "versionFile cannot be empty", data = null))
        }

        if(docRef.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "docRef cannot be empty", data = null))
        }

        if(docName.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "docName cannot be empty", data = null))
        }

        return try {
            val programming = proxy.startTrackedFlow(::IssuerProgramming, issueProgrammingPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${programming.linearId.id} committed to ledger.\n", data = programming))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }


    /***
     *
     * Update Programming Doc
     *
     */
    @PostMapping(value = [ "update-programming-doc" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateProgrammingDoc(
            @RequestBody
            updateProgrammingDocPojo: ProgrammingUpdateDocPojo): ResponseEntity<ResponsePojo> {

        val versionFile = updateProgrammingDocPojo.versionFile
        val bioAgreementCode = updateProgrammingDocPojo.bioAgreementCode
        val docRef = updateProgrammingDocPojo.docRef
        val docName = updateProgrammingDocPojo.docName

        if(versionFile.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "versionFile cannot be empty", data = null))
        }

        if(bioAgreementCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "bioAgreementCode cannot be empty", data = null))
        }

        if(docRef.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "docRef cannot be empty", data = null))
        }

        if(docName.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "docName cannot be empty", data = null))
        }

        return try {
            val updateProgrammingDoc = proxy.startTrackedFlow(::UpdaterProgrammingDoc, updateProgrammingDocPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Programming with bioAgreementCode: $bioAgreementCode update correctly." + "New ProgrammingState with id: ${updateProgrammingDoc.linearId.id} created.. ledger updated.", data = updateProgrammingDoc))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update Programming Status
     *
     */
    @PostMapping(value = [ "update-programming-status" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateProgrammingStatus(
            @RequestBody
            updateProgrammingStatusPojo: ProgrammingUpdateStatusPojo): ResponseEntity<ResponsePojo> {

        val bioAgreementCode = updateProgrammingStatusPojo.bioAgreementCode
        val programmingStatus = updateProgrammingStatusPojo.programmingStatus

        if(bioAgreementCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "bioAgreementCode cannot be empty", data = null))
        }

        if(programmingStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "programmingStatus cannot be empty", data = null))
        }

        return try {
            val updateProgrammingStatus = proxy.startTrackedFlow(::UpdaterProgrammingStatus, updateProgrammingStatusPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Programming with bioAgreementCode: $bioAgreementCode update correctly." + "New ProgrammingState with id: ${updateProgrammingStatus.linearId.id} created.. ledger updated.", data = updateProgrammingStatus))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * RAW MATERIAL API **********************************************************************************************
     *
     */

    /**
     * Displays all Raw Material that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastRawMaterial" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastRawMaterial() : ResponseEntity<ResponsePojo> {
        var foundLastRawMaterialStates = proxy.vaultQueryBy<RawMaterialState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Raw Material list", data = foundLastRawMaterialStates))
    }

    /**
     * Displays last RawMaterialStates that exist in the node's vault for selected CERCode.
     */
    @GetMapping(value = [ "getLastRawMaterialByCERCode/{CERCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastRawMaterialByCERCode(
            @PathVariable("CERCode")
            CERCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for CERCode
        var CERCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {RawMaterialSchemaV1.PersistentRawMaterial::CERCode.equal(CERCode)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(RawMaterialState::class.java))

        val foundCERCodeRawMaterial = proxy.vaultQueryBy<RawMaterialState>(
                CERCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Raw Material by CERCode $CERCode .", data = foundCERCodeRawMaterial))
    }

    /**
     * Initiates a flow to agree an RawMaterial between two nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-rawMaterial" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueRawMaterial(
            @RequestBody
            issueRawMaterialPojo : RawMaterialPojo): ResponseEntity<ResponsePojo> {

        val CERCode = issueRawMaterialPojo.CERCode

        if(CERCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "CERCode cannot be empty", data = null))
        }

        return try {
            val rawMaterial = proxy.startTrackedFlow(::IssuerRawMaterial, issueRawMaterialPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${rawMaterial.linearId.id} committed to ledger.\n", data = rawMaterial))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * BATCH API **********************************************************************************************
     *
     */

    /**
     * Displays all BatchStates that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastBatch" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatch() : ResponseEntity<ResponsePojo> {
        var foundLastBatchStates = proxy.vaultQueryBy<BatchState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Batch list", data = foundLastBatchStates))
    }

    /**
     * Displays last BacthStates that exist in the node's vault for selected batchStatus.
     */
    @GetMapping(value = [ "getLastBatchByStatus/{batchStatus}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatchByStatus(
            @PathVariable("batchStatus")
            batchStatus : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for batchStatus
        var batchStatusCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::batchStatus.equal(batchStatus)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

        val foundBatchStatus = proxy.vaultQueryBy<BatchState>(
                batchStatusCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Batch by batchStatus $batchStatus .", data = foundBatchStatus))
    }

    /**
     * Displays last BatchState that exist in the node's vault for selected produttore (organization name).
     */
    @GetMapping(value = [ "getLastBatchStateByProducer/{producerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatchStateByProducer(
            @PathVariable("producerName")
            producerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for producer organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

        val foundProducerBatch = proxy.vaultQueryBy<BatchState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.produttore.name.organisation == producerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last BatchState by produttore $producerName .", data = foundProducerBatch))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected produttore (organization name).
     */
    @GetMapping(value = [ "getHistoryBatchStateByProducer/{producerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByProducer(
            @PathVariable("producerName")
            producerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for producer organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundProducerBatchHistory = proxy.vaultQueryBy<BatchState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.produttore.name.organisation == producerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $producerName", data = foundProducerBatchHistory))
    }

    /**
     * Displays last BatchState that exist in the node's vault for selected counterpart (organization name).
     */
    @GetMapping(value = [ "getLastBatchStateByCounterpart/{counterpartName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatchStateByCounterpart(
            @PathVariable("counterpartName")
            counterpartName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for counterpart organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

        val foundCounterpartBatch = proxy.vaultQueryBy<BatchState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.counterpart.name.organisation == counterpartName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last BatchState by counterpart $counterpartName .", data = foundCounterpartBatch))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected counterpart (organization name).
     */
    @GetMapping(value = [ "getHistoryBatchStateByCounterpart/{counterpartName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByCounterpart(
            @PathVariable("counterpartName")
            counterpartName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for counterpart organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundCounterpartBatchHistory = proxy.vaultQueryBy<BatchState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.counterpart.name.organisation == counterpartName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $counterpartName", data = foundCounterpartBatchHistory))
    }

    /**
     * Displays last BatchState that exist in the node's vault for selected batchID.
     */
    @GetMapping(value = [ "getLastBatchByID/{batchID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatchByID(
            @PathVariable("batchID")
            batchID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for batchID
        var batchIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::batchID.equal(batchID)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

        val foundBatchID = proxy.vaultQueryBy<BatchState>(
                batchIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last batch by batchID $batchID .", data = foundBatchID))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected batchID.
     */
    @GetMapping(value = [ "getHistoryBatchStateByID/{batchID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByID(
            @PathVariable("batchID")
            batchID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for batchID
        var batchIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::batchID.equal(batchID)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundBatchIDHistory = proxy.vaultQueryBy<BatchState>(
                batchIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $batchID", data = foundBatchIDHistory))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected producer (organization name) and month.
     */
    @GetMapping(value = [ "getHistoryBatchStateByMonthProducer/{month}/{producerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByMonthProducer(
            @PathVariable("month")
            month : String,
            @PathVariable("producerName")
            producerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for producer organization name and month
        var monthCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::month.equal(month)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundMonthProducerBatchHistory = proxy.vaultQueryBy<BatchState>(
                monthCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.produttore.name.organisation == producerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $producerName in $month." , data = foundMonthProducerBatchHistory))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected counterpart (organization name) and month.
     */
    @GetMapping(value = [ "getHistoryBatchStateByMonthCounterpart/{month}/{counterpartName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByMonthCounterpart(
            @PathVariable("month")
            month : String,
            @PathVariable("counterpartName")
            counterpartName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for counterpart organization name and month
        var monthCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::month.equal(month)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundMonthCounterpartBatchHistory = proxy.vaultQueryBy<BatchState>(
                monthCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.counterpart.name.organisation == counterpartName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $counterpartName in $month.", data = foundMonthCounterpartBatchHistory))
    }

    /**
     * Displays last BatchState that exist in the node's vault for selected idProducer.
     */
    @GetMapping(value = [ "getLastBatchByIdProducer/{idProducer}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatchByIdProducer(
            @PathVariable("idProducer")
            idProducer : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for idProducer
        var idProducerCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::idProducer.equal(idProducer)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

        val foundBatchIdProducer = proxy.vaultQueryBy<BatchState>(
                idProducerCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last batch by idProducer $idProducer .", data = foundBatchIdProducer))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected idProducer.
     */
    @GetMapping(value = [ "getHistoryBatchStateByIdProducer/{idProducer}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByIdProducer(
            @PathVariable("idProducer")
            idProducer : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for idProducer
        var batchIdProducerCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::idProducer.equal(idProducer)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundBatchIdProducerHistory = proxy.vaultQueryBy<BatchState>(
                batchIdProducerCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $idProducer", data = foundBatchIdProducerHistory))
    }

    /**
     * Displays last BatchState that exist in the node's vault for selected idCounterpart.
     */
    @GetMapping(value = [ "getLastBatchByIdCounterpart/{idCounterpart}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatchByIdCounterpart(
            @PathVariable("idCounterpart")
            idCounterpart : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for idShipper
        var idCounterpartCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::idCounterpart.equal(idCounterpart)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

        val foundBatchIdCounterpart = proxy.vaultQueryBy<BatchState>(
                idCounterpartCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last batch by idCounterpart $idCounterpart .", data = foundBatchIdCounterpart))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected idCounterpart.
     */
    @GetMapping(value = [ "getHistoryBatchStateByIdCounterpart/{idCounterpart}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByIdCounterpart(
            @PathVariable("idCounterpart")
            idCounterpart : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for idCounterpart
        var batchIdCounterpartCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::idCounterpart.equal(idCounterpart)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundBatchIdCounterpartHistory = proxy.vaultQueryBy<BatchState>(
                batchIdCounterpartCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $idCounterpart", data = foundBatchIdCounterpartHistory))
    }

    /**
     * Initiates a flow to agree an Batch between nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-batch" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueBatch(
            @RequestBody
            issueBatchPojo : BatchPojo): ResponseEntity<ResponsePojo> {

        val produttore = issueBatchPojo.produttore
        val counterpart = issueBatchPojo.counterpart
        val batchID = issueBatchPojo.batchID
        val month = issueBatchPojo.month
        val initialQuantity = issueBatchPojo.initialQuantity
        val quantity = issueBatchPojo.quantity
        val auctionStatus = issueBatchPojo.auctionStatus
        val batchDate = issueBatchPojo.batchDate

        if(produttore.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "produttore (organization name) cannot be empty", data = null))
        }

        if(counterpart.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "counterpart (organization name) cannot be empty", data = null))
        }

        if(batchID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchID cannot be empty", data = null))
        }

        if(month.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "month cannot be empty", data = null))
        }

        if(initialQuantity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "initialQuantity must be a number", data = null))
        }

        if(quantity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "quantity must be a number", data = null))
        }

        if(auctionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "auctionStatus cannot be empty", data = null))
        }

        if(batchDate.toString().isNullOrBlank()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchDate cannot be empty", data = null))
        }

        return try {
            val batch = proxy.startTrackedFlow(::IssuerBatch, issueBatchPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${batch.linearId.id} committed to ledger.\n", data = batch))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update Batch
     *
     */
    @PostMapping(value = [ "update-batch" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateBatch(
            @RequestBody
            updateBatchPojo: BatchUpdatePojo): ResponseEntity<ResponsePojo> {

        val batchId = updateBatchPojo.batchID
        val batchStatus = updateBatchPojo.batchStatus
        val batchSellingQuantity = updateBatchPojo.sellingQuantity
        val batchSellingPrice = updateBatchPojo.sellingPrice

        if(batchId.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchId cannot be empty", data = null))
        }

        if(batchStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchStatus cannot be empty", data = null))
        }

        if(batchSellingQuantity.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "sellingQuantity must be a number", data = null))
        }

        if(batchSellingPrice.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "sellingPrice must be a number", data = null))
        }

        return try {
            val updateBatch = proxy.startTrackedFlow(::UpdaterBatch, updateBatchPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Batch with id: $batchId update correctly. New BatchState with id: ${updateBatch.linearId.id} created.. ledger updated.", data = updateBatch))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update Batch Auction
     *
     */
    @PostMapping(value = [ "update-batch-auction" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateBatchAuction(
            @RequestBody
            updateAuctionBatchPojo: BatchUpdateAuctionPojo): ResponseEntity<ResponsePojo> {

        val batchId = updateAuctionBatchPojo.batchID
        val batchSupportField = updateAuctionBatchPojo.supportField
        val batchAuctionStatus = updateAuctionBatchPojo.auctionStatus

        if(batchId.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchId cannot be empty", data = null))
        }

        if(batchSupportField.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "supportField cannot be empty", data = null))
        }

        if(batchAuctionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "auctionStatus cannot be empty", data = null))
        }

        return try {
            val updateBatchAuction = proxy.startTrackedFlow(::UpdaterBatchAuction, updateAuctionBatchPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Batch with id: $batchId update correctly. New BatchState with id: ${updateBatchAuction.linearId.id} created.. ledger updated.", data = updateBatchAuction))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * EXCHANGE API **********************************************************************************************
     *
     */

    /**
     * Displays all ExchangeStates that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastExchange" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastExchange() : ResponseEntity<ResponsePojo> {
        var foundLastExchangeStates = proxy.vaultQueryBy<ExchangeState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Exchange list", data = foundLastExchangeStates))
    }

    /**
     * Displays last ExchangeStates that exist in the node's vault for selected exchangeStatus.
     */
    @GetMapping(value = [ "getLastExchangeByStatus/{exchangeStatus}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastExchangeByStatus(
            @PathVariable("exchangeStatus")
            exchangeStatus : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for exchangeStatus
        var exchangeStatusCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ExchangeSchemaV1.PersistentExchange::exchangeStatus.equal(exchangeStatus)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ExchangeState::class.java))

        val foundExchangeStatus = proxy.vaultQueryBy<ExchangeState>(
                exchangeStatusCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last Exchange by exchangeStatus $exchangeStatus .", data = foundExchangeStatus))
    }

    /**
     * Displays last ExchangeState that exist in the node's vault for selected seller (organization name).
     */
    @GetMapping(value = [ "getLastExchangeStateBySeller/{sellerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastExchangeStateBySeller(
            @PathVariable("sellerName")
            sellerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for seller organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ExchangeState::class.java))

        val foundSellerExchange = proxy.vaultQueryBy<ExchangeState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.seller.name.organisation == sellerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last ExchangeState by seller $sellerName .", data = foundSellerExchange))
    }

    /**
     * Displays History ExchangeState that exist in the node's vault for selected seller (organization name).
     */
    @GetMapping(value = [ "getHistoryExchangeStateBySeller/{sellerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryExchangeStateBySeller(
            @PathVariable("sellerName")
            sellerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for seller organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(ExchangeState::class.java))

        val foundSellerExchangeHistory = proxy.vaultQueryBy<ExchangeState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.seller.name.organisation == sellerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of exchange state for $sellerName", data = foundSellerExchangeHistory))
    }

    /**
     * Displays last ExchangeState that exist in the node's vault for selected buyer (organization name).
     */
    @GetMapping(value = [ "getLastExchangeStateByBuyer/{buyerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastExchangeStateByBuyer(
            @PathVariable("buyerName")
            buyerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for buyer organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ExchangeState::class.java))

        val foundBuyerExchange = proxy.vaultQueryBy<ExchangeState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.buyer.name.organisation == buyerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last ExchangeState by buyer $buyerName .", data = foundBuyerExchange))
    }

    /**
     * Displays History ExchangeState that exist in the node's vault for selected buyer (organization name).
     */
    @GetMapping(value = [ "getHistoryExchangeStateByBuyer/{buyerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryExchangeStateByBuyer(
            @PathVariable("buyerName")
            buyerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for buyer organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(ExchangeState::class.java))

        val foundBuyerExchangeHistory = proxy.vaultQueryBy<ExchangeState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.buyer.name.organisation == buyerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of exchange state for $buyerName", data = foundBuyerExchangeHistory))
    }

    /**
     * Displays last ExchangeState that exist in the node's vault for selected exchangeCode.
     */
    @GetMapping(value = [ "getLastExchangeByCode/{exchangeCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastExchangeByCode(
            @PathVariable("exchangeCode")
            exchangeCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for exchangeCode
        var exchangeCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ExchangeSchemaV1.PersistentExchange::exchangeCode.equal(exchangeCode)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ExchangeState::class.java))

        val foundExchangeCode = proxy.vaultQueryBy<ExchangeState>(
                exchangeCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last exchange by exchangeCode $exchangeCode .", data = foundExchangeCode))
    }

    /**
     * Displays History ExchangeState that exist in the node's vault for selected exchangeCode.
     */
    @GetMapping(value = [ "getHistoryExchangeStateByCode/{exchangeCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryExchangeStateByCode(
            @PathVariable("exchangeCode")
            exchangeCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for exchangeCode
        var exchangeCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ExchangeSchemaV1.PersistentExchange::exchangeCode.equal(exchangeCode)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ExchangeState::class.java))

        val foundExchangeCodeHistory = proxy.vaultQueryBy<ExchangeState>(
                exchangeCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of exchange state for $exchangeCode", data = foundExchangeCodeHistory))
    }

    /**
     * Displays last ExchangeState that exist in the node's vault for selected parentBatchID.
     */
    @GetMapping(value = [ "getLastExchangeByParent/{parentBatchID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastExchangeByParent(
            @PathVariable("parentBatchID")
            parentBatchID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for parentBatchID
        var exchangeParentCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ExchangeSchemaV1.PersistentExchange::parentBatchID.equal(parentBatchID)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ExchangeState::class.java))

        val foundExchangeParent = proxy.vaultQueryBy<ExchangeState>(
                exchangeParentCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last exchange by parentBatchID $parentBatchID .", data = foundExchangeParent))
    }

    /**
     * Displays History ExchangeState that exist in the node's vault for selected parentBatchID.
     */
    @GetMapping(value = [ "getHistoryExchangeStateByParent/{parentBatchID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryExchangeStateByParent(
            @PathVariable("parentBatchID")
            parentBatchID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for parentBatchID
        var exchangeParentCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ExchangeSchemaV1.PersistentExchange::parentBatchID.equal(parentBatchID)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ExchangeState::class.java))

        val foundExchangeParentHistory = proxy.vaultQueryBy<ExchangeState>(
                exchangeParentCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of exchange state for $parentBatchID", data = foundExchangeParentHistory))
    }

    /**
     * Displays History ExchangeState that exist in the node's vault for selected seller (organization name) and month.
     */
    @GetMapping(value = [ "getHistoryExchangeStateByMonthSeller/{month}/{sellerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryExchangeStateByMonthSeller(
            @PathVariable("month")
            month : String,
            @PathVariable("sellerName")
            sellerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for seller organization name and month
        var monthCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ExchangeSchemaV1.PersistentExchange::month.equal(month)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ExchangeState::class.java))

        val foundMonthSellerExchangeHistory = proxy.vaultQueryBy<ExchangeState>(
                monthCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.seller.name.organisation == sellerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of exchange state for $sellerName in $month." , data = foundMonthSellerExchangeHistory))
    }

    /**
     * Displays History ExchangeState that exist in the node's vault for selected buyer (organization name) and month.
     */
    @GetMapping(value = [ "getHistoryExchangeStateByMonthBuyer/{month}/{buyerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryExchangeStateByMonthBuyer(
            @PathVariable("month")
            month : String,
            @PathVariable("buyerName")
            buyerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for buyer organization name and month
        var monthCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ExchangeSchemaV1.PersistentExchange::month.equal(month)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ExchangeState::class.java))

        val foundMonthBuyerExchangeHistory = proxy.vaultQueryBy<ExchangeState>(
                monthCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.buyer.name.organisation == buyerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of exchange state for $buyerName in $month.", data = foundMonthBuyerExchangeHistory))
    }

    /**
     * Initiates a flow to agree an Exchange between nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-exchange" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueExchange(
            @RequestBody
            issueExchangePojo : ExchangePojo): ResponseEntity<ResponsePojo> {

        val seller = issueExchangePojo.seller
        val buyer = issueExchangePojo.buyer
        val exchangeCode = issueExchangePojo.exchangeCode
        val month = issueExchangePojo.month
        val parentBatchID = issueExchangePojo.parentBatchID
        val initialQuantity = issueExchangePojo.initialQuantity
        val quantity = issueExchangePojo.quantity
        val pcs = issueExchangePojo.pcs
        val pci = issueExchangePojo.pci
        val auctionStatus = issueExchangePojo.auctionStatus

        if(seller.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "seller (organization name) cannot be empty", data = null))
        }

        if(buyer.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "buyer (organization name) cannot be empty", data = null))
        }

        if(exchangeCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "exchangeCode cannot be empty", data = null))
        }

        if(month.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "month cannot be empty", data = null))
        }

        if(parentBatchID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "parentBatchID cannot be empty", data = null))
        }

        if(initialQuantity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "initialQuantity must be a number", data = null))
        }

        if(quantity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "quantity must be a number", data = null))
        }

        if(pcs.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "pcs must be a number", data = null))
        }

        if(pci.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "pci must be a number", data = null))
        }

        if(auctionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "auctionStatus cannot be empty", data = null))
        }

        return try {
            val exchange = proxy.startTrackedFlow(::IssuerExchange, issueExchangePojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${exchange.linearId.id} committed to ledger.\n", data = exchange))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update Exchange
     *
     */
    @PostMapping(value = [ "update-exchange" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateExchange(
            @RequestBody
            updateExchangePojo: ExchangeUpdatePojo): ResponseEntity<ResponsePojo> {

        val exchangeCode = updateExchangePojo.exchangeCode
        val exchangeStatus = updateExchangePojo.exchangeStatus
        val exchangeSellingQuantity = updateExchangePojo.sellingQuantity
        val exchangeSellingPrice = updateExchangePojo.sellingPrice

        if(exchangeCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "exchangeCode cannot be empty", data = null))
        }

        if(exchangeStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "exchangeStatus cannot be empty", data = null))
        }

        if(exchangeSellingQuantity.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "sellingQuantity must be a number", data = null))
        }

        if(exchangeSellingPrice.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "sellingPrice must be a number", data = null))
        }

        return try {
            val updateExchange = proxy.startTrackedFlow(::UpdaterExchange, updateExchangePojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Exchange with id: $exchangeCode update correctly. New ExchangeState with id: ${updateExchange.linearId.id} created.. ledger updated.", data = updateExchange))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update Exchange Auction
     *
     */
    @PostMapping(value = [ "update-exchange-auction" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateExchangeAuction(
            @RequestBody
            updateAuctionExchangePojo: ExchangeUpdateAuctionPojo): ResponseEntity<ResponsePojo> {

        val exchangeCode = updateAuctionExchangePojo.exchangeCode
        val exchangeSupportField = updateAuctionExchangePojo.supportField
        val exchangeAuctionStatus = updateAuctionExchangePojo.auctionStatus

        if(exchangeCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "exchangeCode cannot be empty", data = null))
        }

        if(exchangeSupportField.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "supportField cannot be empty", data = null))
        }

        if(exchangeAuctionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "auctionStatus cannot be empty", data = null))
        }

        return try {
            val updateAuctionExchange = proxy.startTrackedFlow(::UpdaterExchangeAuction, updateAuctionExchangePojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Exchange with id: $exchangeCode update correctly. New ExchangeState with id: ${updateAuctionExchange.linearId.id} created.. ledger updated.", data = updateAuctionExchange))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * PSVSTATE API **********************************************************************************************
     *
     */

    /**
     * Displays all PSVStates that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastPSVState" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastPSVState() : ResponseEntity<ResponsePojo> {
        var foundLastPSVStates = proxy.vaultQueryBy<PSVState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "PSVState list", data = foundLastPSVStates))
    }

    /**
     * Displays last PSVStates that exist in the node's vault for selected transactionStatus.
     */
    @GetMapping(value = [ "getLastPSVStateByStatus/{transactionStatus}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastPSVStateByStatus(
            @PathVariable("transactionStatus")
            transactionStatus : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for transactionStatus
        var transactionStatusCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {PSVSchemaV1.PersistentPSV::transactionStatus.equal(transactionStatus)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

        val foundPSVStatus = proxy.vaultQueryBy<PSVState>(
                transactionStatusCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.hostname == hostname }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last PSVState by transactionStatus $transactionStatus .", data = foundPSVStatus))
    }

    /**
     * Displays last PSVState that exist in the node's vault for selected seller (organization name).
     */
    @GetMapping(value = [ "getLastPSVStateBySeller/{sellerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastPSVStateBySeller(
            @PathVariable("sellerName")
            sellerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for seller organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

        val foundSellerPSVState = proxy.vaultQueryBy<PSVState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.seller.name.organisation == sellerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last PSVState by seller $sellerName .", data = foundSellerPSVState))
    }

    /**
     * Displays History PSVState that exist in the node's vault for selected seller (organization name).
     */
    @GetMapping(value = [ "getHistoryPSVStateBySeller/{sellerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryPSVStateBySeller(
            @PathVariable("sellerName")
            sellerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for seller organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(PSVState::class.java))

        val foundSellerPSVHistory = proxy.vaultQueryBy<PSVState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.seller.name.organisation == sellerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of psv state for $sellerName", data = foundSellerPSVHistory))
    }

    /**
     * Displays last PSVState that exist in the node's vault for selected buyer (organization name).
     */
    @GetMapping(value = [ "getLastPSVStateByBuyer/{buyerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastPSVStateByBuyer(
            @PathVariable("buyerName")
            buyerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for buyer organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

        val foundBuyerPSVState = proxy.vaultQueryBy<PSVState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.buyer.name.organisation == buyerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last PSVState by buyer $buyerName .", data = foundBuyerPSVState))
    }

    /**
     * Displays History PSVState that exist in the node's vault for selected buyer (organization name).
     */
    @GetMapping(value = [ "getHistoryPSVStateByBuyer/{buyerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryPSVStateByBuyer(
            @PathVariable("buyerName")
            buyerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for buyer organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(PSVState::class.java))

        val foundBuyerPSVHistory = proxy.vaultQueryBy<PSVState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.buyer.name.organisation == buyerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of psv state for $buyerName", data = foundBuyerPSVHistory))
    }

    /**
     * Displays last PSVState that exist in the node's vault for selected transactionCode.
     */
    @GetMapping(value = [ "getLastPSVStateByCode/{transactionCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastPSVStateByCode(
            @PathVariable("transactionCode")
            transactionCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for transactionCode
        var transactionCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {PSVSchemaV1.PersistentPSV::transactionCode.equal(transactionCode)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

        val foundPSVStateCode = proxy.vaultQueryBy<PSVState>(
                transactionCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last psvState by transactionCode $transactionCode .", data = foundPSVStateCode))
    }

    /**
     * Displays History PSVState that exist in the node's vault for selected transactionCode.
     */
    @GetMapping(value = [ "getHistoryPSVStateByCode/{transactionCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryPSVStateByCode(
            @PathVariable("transactionCode")
            transactionCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for transactionCode
        var transactionCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {PSVSchemaV1.PersistentPSV::transactionCode.equal(transactionCode)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(PSVState::class.java))

        val foundPSVStateCodeHistory = proxy.vaultQueryBy<PSVState>(
                transactionCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of psv state for $transactionCode", data = foundPSVStateCodeHistory))
    }

    /**
     * Displays last PSVState that exist in the node's vault for selected parentBatchID.
     */
    @GetMapping(value = [ "getLastPSVStateByParent/{parentBatchID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastPSVStateByParent(
            @PathVariable("parentBatchID")
            parentBatchID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for parentBatchID
        var psvParentCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {PSVSchemaV1.PersistentPSV::parentBatchID.equal(parentBatchID)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

        val foundPSVStateParent = proxy.vaultQueryBy<PSVState>(
                psvParentCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last psvState by parentBatchID $parentBatchID .", data = foundPSVStateParent))
    }

    /**
     * Displays History PSVState that exist in the node's vault for selected parentBatchID.
     */
    @GetMapping(value = [ "getHistoryPSVStateByParent/{parentBatchID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryPSVStateByParent(
            @PathVariable("parentBatchID")
            parentBatchID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for parentBatchID
        var psvParentCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {PSVSchemaV1.PersistentPSV::parentBatchID.equal(parentBatchID)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(PSVState::class.java))

        val foundPSVStateParentHistory = proxy.vaultQueryBy<PSVState>(
                psvParentCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of psv state for $parentBatchID", data = foundPSVStateParentHistory))
    }

    /**
     * Displays History PSVState that exist in the node's vault for selected seller (organization name) and month.
     */
    @GetMapping(value = [ "getHistoryPSVStateByMonthSeller/{month}/{sellerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryPSVStateByMonthSeller(
            @PathVariable("month")
            month : String,
            @PathVariable("sellerName")
            sellerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for seller organization name and month
        var monthCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {PSVSchemaV1.PersistentPSV::month.equal(month)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(PSVState::class.java))

        val foundMonthSellerPSVHistory = proxy.vaultQueryBy<PSVState>(
                monthCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.seller.name.organisation == sellerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of psv state for $sellerName in $month." , data = foundMonthSellerPSVHistory))
    }

    /**
     * Displays History PSVState that exist in the node's vault for selected buyer (organization name) and month.
     */
    @GetMapping(value = [ "getHistoryPSVStateByMonthBuyer/{month}/{buyerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryPSVStateByMonthBuyer(
            @PathVariable("month")
            month : String,
            @PathVariable("buyerName")
            buyerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for buyer organization name and month
        var monthCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {PSVSchemaV1.PersistentPSV::month.equal(month)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(PSVState::class.java))

        val foundMonthBuyerPSVHistory = proxy.vaultQueryBy<PSVState>(
                monthCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.buyer.name.organisation == buyerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of psv state for $buyerName in $month.", data = foundMonthBuyerPSVHistory))
    }

    /**
     * Initiates a flow to agree an PSVState between nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-psv" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issuePSVState(
            @RequestBody
            issuePSVPojo : PSVPojo): ResponseEntity<ResponsePojo> {

        val seller = issuePSVPojo.seller
        val buyer = issuePSVPojo.buyer
        val transactionCode = issuePSVPojo.transactionCode
        val month = issuePSVPojo.month
        val parentBatchID = issuePSVPojo.parentBatchID
        val initialQuantity = issuePSVPojo.initialQuantity
        val quantity = issuePSVPojo.quantity
        val auctionStatus = issuePSVPojo.auctionStatus

        if(seller.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "seller (organization name) cannot be empty", data = null))
        }

        if(buyer.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "buyer (organization name) cannot be empty", data = null))
        }

        if(transactionCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "transactionCode cannot be empty", data = null))
        }

        if(month.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "month cannot be empty", data = null))
        }

        if(parentBatchID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "parentBatchID cannot be empty", data = null))
        }

        if(initialQuantity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "initialQuantity must be a number", data = null))
        }

        if(quantity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "quantity must be a number", data = null))
        }

        if(auctionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "auctionStatus cannot be empty", data = null))
        }

        return try {
            val psvState = proxy.startTrackedFlow(::IssuerPSVState, issuePSVPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${psvState.linearId.id} committed to ledger.\n", data = psvState))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update PSVState
     *
     */
    @PostMapping(value = [ "update-psv" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updatePSVState(
            @RequestBody
            updatePSVPojo: PSVUpdatePojo): ResponseEntity<ResponsePojo> {

        val transactionCode = updatePSVPojo.transactionCode
        val transactionStatus = updatePSVPojo.transactionStatus
        val transactionSellingQuantity = updatePSVPojo.sellingQuantity
        val transactionSellingPrice = updatePSVPojo.sellingPrice

        if(transactionCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "transactionCode cannot be empty", data = null))
        }

        if(transactionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "transactionStatus cannot be empty", data = null))
        }

        if(transactionSellingQuantity.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "sellingQuantity must be a number", data = null))
        }

        if(transactionSellingPrice.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "sellingPrice must be a number", data = null))
        }

        return try {
            val updatePSVState = proxy.startTrackedFlow(::UpdaterPSVState, updatePSVPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "PSV with id: $transactionCode update correctly. New PSVState with id: ${updatePSVState.linearId.id} created.. ledger updated.", data = updatePSVState))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update PSVState Auction
     *
     */
    @PostMapping(value = [ "update-psv-auction" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updatePSVStateAuction(
            @RequestBody
            updateAuctionPSVPojo: PSVUpdateAuctionPojo): ResponseEntity<ResponsePojo> {

        val transactionCode = updateAuctionPSVPojo.transactionCode
        val transactionSupportField = updateAuctionPSVPojo.supportField
        val transactionAuctionStatus = updateAuctionPSVPojo.auctionStatus

        if(transactionCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "transactionCode cannot be empty", data = null))
        }

        if(transactionSupportField.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "supportField cannot be empty", data = null))
        }

        if(transactionAuctionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "auctionStatus cannot be empty", data = null))
        }

        return try {
            val updateAuctionPSVState = proxy.startTrackedFlow(::UpdaterPSVStateAuction, updateAuctionPSVPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "PSV with id: $transactionCode update correctly. New PSVState with id: ${updateAuctionPSVState.linearId.id} created.. ledger updated.", data = updateAuctionPSVState))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * WALLETREWARD API **********************************************************************************************
     *
     */

    /**
     * Displays all WalletRewardState that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastWalletReward" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastWalletReward() : ResponseEntity<ResponsePojo> {
        var foundLastWalletRewardStates = proxy.vaultQueryBy<WalletRewardState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "WalletReward list", data = foundLastWalletRewardStates))
    }

    /**
     * Displays last WalletRewardState that exist in the node's vault for selected owner.
     */
    @GetMapping(value = [ "getLastWalletRewardStateByOwner/{ownerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastWalletRewardStateByOwner(
            @PathVariable("ownerName")
            ownerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for owner organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(WalletRewardState::class.java))

        val foundOwnerWalletRewardState = proxy.vaultQueryBy<WalletRewardState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.owner.name.organisation == ownerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last WalletRewardState by owner $ownerName .", data = foundOwnerWalletRewardState))
    }

    /**
     * Displays History WalletRewardState that exist in the node's vault for selected owner (organization name).
     */
    @GetMapping(value = [ "getHistoryWalletRewardStateByOwner/{ownerName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryWalletRewardStateByOwner(
            @PathVariable("ownerName")
            ownerName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for owner organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(WalletRewardState::class.java))

        val foundOwnerWalletRewardHistory = proxy.vaultQueryBy<WalletRewardState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.owner.name.organisation == ownerName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of walletReward state for $ownerName", data = foundOwnerWalletRewardHistory))
    }

    /**
     * Displays last WalletRewardState that exist in the node's vault for selected walletID.
     */
    @GetMapping(value = [ "getLastWalletRewardByID/{walletID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastWalletRewardByID(
            @PathVariable("walletID")
            walletID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for walletID
        var walletIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {WalletRewardSchemaV1.PersistentWalletReward::walletID.equal(walletID)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(WalletRewardState::class.java))

        val foundLastWalletID = proxy.vaultQueryBy<WalletRewardState>(
                walletIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last WalletRewardState by walletID $walletID .", data = foundLastWalletID))
    }

    /**
     * Displays History WalletRewardState that exist in the node's vault for selected walletID.
     */
    @GetMapping(value = [ "getHistoryWalletRewardByID/{walletID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryWalletRewardByID(
            @PathVariable("walletID")
            walletID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for walletID
        var walletIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {WalletRewardSchemaV1.PersistentWalletReward::walletID.equal(walletID)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(WalletRewardState::class.java))

        val foundWalletIDHistory = proxy.vaultQueryBy<WalletRewardState>(
                walletIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of walletReward state for $walletID", data = foundWalletIDHistory))
    }

    /**
     * Initiates a flow to agree an WalletReward between two nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-wallet-reward" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueWalletReward(
            @RequestBody
            issueWalletReward : WalletRewardPojo): ResponseEntity<ResponsePojo> {

        val walletID = issueWalletReward.walletID
        val rewardPoint = issueWalletReward.rewardPoint
        val reason = issueWalletReward.reason

        if(walletID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "walletID cannot be empty", data = null))
        }

        if(rewardPoint <= 0) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "rewardPoint must be greater than zero", data = null))
        }

        if(reason.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "reason cannot be empty", data = null))
        }

        return try {
            val walletReward = proxy.startTrackedFlow(::IssuerWalletReward, issueWalletReward).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${walletReward.linearId.id} committed to ledger.\n", data = walletReward))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update WalletReward
     *
     */
    @PostMapping(value = [ "update-wallet-reward" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateWalletReward(
            @RequestBody
            updateWalletRewardPojo: WalletRewardUpdatePojo): ResponseEntity<ResponsePojo> {

        val walletID = updateWalletRewardPojo.walletID
        val rewardPoint = updateWalletRewardPojo.rewardPoint
        val reason = updateWalletRewardPojo.reason

        if(walletID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "walletID cannot be empty", data = null))
        }

        if(rewardPoint < 0.0) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "rewardPoint must be a >= 0.0 number", data = null))
        }

        if(reason.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "reason cannot be empty", data = null))
        }

        return try {
            val updateWalletReward = proxy.startTrackedFlow(::UpdaterWalletReward, updateWalletRewardPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "WalletReward with id: $walletID update correctly. New WalletRewardState with id: ${updateWalletReward.linearId.id} created.. ledger updated.", data = updateWalletReward))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * AGREEMENT API **********************************************************************************************
     *
     */

    /**
     * Displays all AgreementState that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastAgreement" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastAgreement() : ResponseEntity<ResponsePojo> {
        var foundLastAgreementStates = proxy.vaultQueryBy<AgreementState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "AgreementState list", data = foundLastAgreementStates))
    }

    /**
     * Displays last AgreementState that exist in the node's vault for selected owner.
     */
    @GetMapping(value = [ "getLastAgreementStateByOwner/{owner}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastAgreementStateByOwner(
            @PathVariable("owner")
            owner : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for owner
        var ownerCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {AgreementSchemaV1.PersistentAgreement::owner.equal(owner)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(AgreementState::class.java))

        val foundOwnerLastAgreement = proxy.vaultQueryBy<AgreementState>(
                ownerCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last AgreementState by owner $owner .", data = foundOwnerLastAgreement))
    }

    /**
     * Displays History AgreementState that exist in the node's vault for selected owner.
     */
    @GetMapping(value = [ "getHistoryAgreementStateByOwner/{owner}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryAgreementStateByOwner(
            @PathVariable("owner")
            owner : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for owner
        var ownerCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {AgreementSchemaV1.PersistentAgreement::owner.equal(owner)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(AgreementState::class.java))

        val foundOwnerAgreementHistory = proxy.vaultQueryBy<AgreementState>(
                ownerCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of agreement state for $owner", data = foundOwnerAgreementHistory))
    }

    /**
     * Displays last AgreementState that exist in the node's vault for selected counterpart.
     */
    @GetMapping(value = [ "getLastAgreementStateByCounterpart/{counterpart}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastAgreementStateByCounterpart(
            @PathVariable("counterpart")
            counterpart : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for counterpart
        var counterpartCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {AgreementSchemaV1.PersistentAgreement::counterpart.equal(counterpart)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(AgreementState::class.java))

        val foundCounterpartLastAgreement = proxy.vaultQueryBy<AgreementState>(
                counterpartCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last AgreementState by counterpart $counterpart .", data = foundCounterpartLastAgreement))
    }

    /**
     * Displays History AgreementState that exist in the node's vault for selected counterpart.
     */
    @GetMapping(value = [ "getHistoryAgreementStateByCounterpart/{counterpart}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryAgreementStateByCounterpart(
            @PathVariable("counterpart")
            counterpart : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for counterpart
        var counterpartCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {AgreementSchemaV1.PersistentAgreement::counterpart.equal(counterpart)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(AgreementState::class.java))

        val foundCounterpartAgreementHistory = proxy.vaultQueryBy<AgreementState>(
                counterpartCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of agreement state for $counterpart", data = foundCounterpartAgreementHistory))
    }

    /**
     * Displays last AgreementState that exist in the node's vault for selected agreementID.
     */
    @GetMapping(value = [ "getLastAgreementStateByID/{agreementID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastAgreementStateByID(
            @PathVariable("agreementID")
            agreementID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for counterpart
        var agreementIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {AgreementSchemaV1.PersistentAgreement::agreementID.equal(agreementID)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(AgreementState::class.java))

        val foundIDLastAgreement = proxy.vaultQueryBy<AgreementState>(
                agreementIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last AgreementState by agreementID $agreementID .", data = foundIDLastAgreement))
    }

    /**
     * Displays History AgreementState that exist in the node's vault for selected agreementID.
     */
    @GetMapping(value = [ "getHistoryAgreementStateByID/{agreementID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryAgreementStateByID(
            @PathVariable("agreementID")
            agreementID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for counterpart
        var agreementIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {AgreementSchemaV1.PersistentAgreement::agreementID.equal(agreementID)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(AgreementState::class.java))

        val foundIDAgreementHistory = proxy.vaultQueryBy<AgreementState>(
                agreementIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of agreement state for $agreementID", data = foundIDAgreementHistory))
    }

    /**
     * Initiates a flow to agree an AgreementState between two nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-agreement" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueAgreement(
            @RequestBody
            issueAgreement : AgreementPojo): ResponseEntity<ResponsePojo> {

        val counterpartParty = issueAgreement.counterpartParty
        val agreementID = issueAgreement.agreementID
        val agreementCode = issueAgreement.agreementCode
        val agreementType = issueAgreement.agreementType
        val agreementSubType = issueAgreement.agreementSubType
        val owner = issueAgreement.owner
        val counterpart = issueAgreement.counterpart
        val remiCode = issueAgreement.remiCode
        val averageDailyCapacity = issueAgreement.averageDailyCapacity
        val maxDailyCapacity = issueAgreement.maxDailyCapacity
        val energyEstimation = issueAgreement.energyEstimation
        val energy = issueAgreement.energy
        val dcq = issueAgreement.dcq
        val price = issueAgreement.price
        val validFrom = issueAgreement.validFrom
        val validTo = issueAgreement.validTo
        val agreementStatus = issueAgreement.agreementStatus

        if(counterpartParty.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "counterpartParty cannot be empty", data = null))
        }

        if(agreementID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementID cannot be empty", data = null))
        }

        if(agreementCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementCode cannot be empty", data = null))
        }

        if(agreementType.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementType cannot be empty", data = null))
        }

        if(agreementSubType.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementSubType cannot be empty", data = null))
        }

        if(owner.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "owner cannot be empty", data = null))
        }

        if(counterpart.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "counterpart cannot be empty", data = null))
        }

        if(remiCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "remiCode cannot be empty", data = null))
        }

        if(averageDailyCapacity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "averageDailyCapacity must be a number", data = null))
        }

        if(maxDailyCapacity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "maxDailyCapacity must be a number", data = null))
        }

        if(energyEstimation.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "energyEstimation must be a number", data = null))
        }

        if(energy.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "energy must be a number", data = null))
        }

        if(dcq.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "dcq must be a number", data = null))
        }

        if(price.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "price must be a number", data = null))
        }

        if(validFrom.toString().isBlank()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "validFrom cannot be empty", data = null))
        }

        if(validTo.toString().isBlank()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "validTo cannot be empty", data = null))
        }

        if(agreementStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementStatus cannot be empty", data = null))
        }

        return try {
            val agreement = proxy.startTrackedFlow(::IssuerAgreement, issueAgreement).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${agreement.linearId.id} committed to ledger.\n", data = agreement))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update Agreement
     *
     */
    @PostMapping(value = [ "update-agreement" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateAgreement(
            @RequestBody
            updateAgreementPojo: AgreementUpdatePojo): ResponseEntity<ResponsePojo> {

        val agreementID = updateAgreementPojo.agreementID
        val agreementEnergy = updateAgreementPojo.energy
        val agreementDcq = updateAgreementPojo.dcq
        val agreementPrice = updateAgreementPojo.price
        val agreementValidFrom = updateAgreementPojo.validFrom
        val agreementValidTo = updateAgreementPojo.validTo

        if(agreementID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementID cannot be empty", data = null))
        }

        if(agreementEnergy.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementEnergy must be a number", data = null))
        }

        if(agreementDcq.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "dcq must be a number", data = null))
        }

        if(agreementPrice.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "price must be a number", data = null))
        }

        if(agreementValidFrom.toString().isBlank()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "validFrom cannot be empty", data = null))
        }

        if(agreementValidTo.toString().isBlank()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "validTo cannot be empty", data = null))
        }

        return try {
            val updateAgreement = proxy.startTrackedFlow(::UpdaterAgreement, updateAgreementPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Agreement with id: $agreementID update correctly. New AgreementState with id: ${updateAgreement.linearId.id} created.. ledger updated.", data = updateAgreement))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update Agreement Status
     *
     */
    @PostMapping(value = [ "update-agreement-status" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateAgreementStatus(
            @RequestBody
            updateAgreementStatusPojo: AgreementUpdateStatusPojo): ResponseEntity<ResponsePojo> {

        val agreementID = updateAgreementStatusPojo.agreementID
        val agreementStatus = updateAgreementStatusPojo.agreementStatus

        if(agreementID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementID cannot be empty", data = null))
        }

        if(agreementStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "agreementStatus cannot be empty", data = null))
        }

        return try {
            val updateAgreement = proxy.startTrackedFlow(::UpdaterAgreementStatus, updateAgreementStatusPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Agreement with id: $agreementID update correctly. New AgreementState with id: ${updateAgreement.linearId.id} created.. ledger updated.", data = updateAgreement))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /**
     *
     * REPORT API **********************************************************************************************
     *
     */

    /**
     * Displays all ReportState that exist in the node's vault.
     */
    @GetMapping(value = [ "getLastReport" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastReport() : ResponseEntity<ResponsePojo> {
        var foundLastReportStates = proxy.vaultQueryBy<ReportState>(
                paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000)).states
        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "ReportState list", data = foundLastReportStates))
    }

    /**
     * Displays last ReportState that exist in the node's vault for selected ownerID.
     */
    @GetMapping(value = [ "getLastReportStateByOwnerID/{ownerID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastReportStateByOwnerID(
            @PathVariable("ownerID")
            ownerID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for ownerID
        var ownerIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ReportSchemaV1.PersistentReport::ownerID.equal(ownerID)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ReportState::class.java))

        val foundOwnerIDLastReport = proxy.vaultQueryBy<ReportState>(
                ownerIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last ReportState by ownerID $ownerID .", data = foundOwnerIDLastReport))
    }

    /**
     * Displays History ReportState that exist in the node's vault for selected ownerID.
     */
    @GetMapping(value = [ "getHistoryReportStateByOwnerID/{ownerID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryReportStateByOwnerID(
            @PathVariable("ownerID")
            ownerID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for ownerID
        var ownerIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ReportSchemaV1.PersistentReport::ownerID.equal(ownerID)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ReportState::class.java))

        val foundOwnerIDReportHistory = proxy.vaultQueryBy<ReportState>(
                ownerIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of report state for $ownerID", data = foundOwnerIDReportHistory))
    }

    /**
     * Displays last ReportState that exist in the node's vault for selected reportID.
     */
    @GetMapping(value = [ "getLastReportStateByReportID/{reportID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastReportStateByReportID(
            @PathVariable("reportID")
            reportID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for reportID
        var reportIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ReportSchemaV1.PersistentReport::reportID.equal(reportID)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ReportState::class.java))

        val foundReportIDLastReport = proxy.vaultQueryBy<ReportState>(
                reportIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last ReportState by reportID $reportID .", data = foundReportIDLastReport))
    }

    /**
     * Displays History ReportState that exist in the node's vault for selected reportID.
     */
    @GetMapping(value = [ "getHistoryReportStateByReportID/{reportID}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryReportStateByReportID(
            @PathVariable("reportID")
            reportID : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for reportID
        var reportIDCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ReportSchemaV1.PersistentReport::reportID.equal(reportID)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ReportState::class.java))

        val foundReportIDReportHistory = proxy.vaultQueryBy<ReportState>(
                reportIDCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of report state for $reportID", data = foundReportIDReportHistory))
    }

    /**
     * Displays last ReportState that exist in the node's vault for selected remiCode.
     */
    @GetMapping(value = [ "getLastReportStateByRemiCode/{remiCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastReportStateByRemiCode(
            @PathVariable("remiCode")
            remiCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for remiCode
        var remiCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ReportSchemaV1.PersistentReport::remiCode.equal(remiCode)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ReportState::class.java))

        val foundRemiCodeLastReport = proxy.vaultQueryBy<ReportState>(
                remiCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last ReportState by remiCode $remiCode .", data = foundRemiCodeLastReport))
    }

    /**
     * Displays History ReportState that exist in the node's vault for selected remiCode.
     */
    @GetMapping(value = [ "getHistoryReportStateByRemiCode/{remiCode}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryReportStateByRemiCode(
            @PathVariable("remiCode")
            remiCode : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for remiCode
        var remiCodeCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {ReportSchemaV1.PersistentReport::remiCode.equal(remiCode)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(ReportState::class.java))

        val foundRemiCodeReportHistory = proxy.vaultQueryBy<ReportState>(
                remiCodeCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states
        //.filter { it.state.data.macAddress == macAddress }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of report state for $remiCode", data = foundRemiCodeReportHistory))
    }

    /**
     * Initiates a flow to agree an ReportState between two nodes.
     *
     * Once the flow finishes it will have written the Measure to ledger. Both NodeA, NodeB are able to
     * see it when calling /api/bioMetanoRevenge/ on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PostMapping(value = [ "issue-report" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/json" ])
    fun issueReport(
            @RequestBody
            issueReportpojo : ReportPojo): ResponseEntity<ResponsePojo> {

        val ownerNodeParty = issueReportpojo.ownerNode
        val ownerID = issueReportpojo.ownerID
        val reportID = issueReportpojo.reportID
        val reportType = issueReportpojo.reportType
        val remiCode = issueReportpojo.remiCode
        val remiAddress = issueReportpojo.remiAddress
        val measuredQuantity = issueReportpojo.measuredQuantity
        val measuredEnergy = issueReportpojo.measuredEnergy
        val measuredPcs = issueReportpojo.measuredPcs
        val measuredPci = issueReportpojo.measuredPci

        if(ownerNodeParty.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "ownerNodeParty cannot be empty", data = null))
        }

        if(ownerID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "ownerID cannot be empty", data = null))
        }

        if(reportID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "reportID cannot be empty", data = null))
        }

        if(reportType.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "reportType cannot be empty", data = null))
        }

        if(remiCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "remiCode cannot be empty", data = null))
        }

        if(remiAddress.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "remiAddress cannot be empty", data = null))
        }

        if(measuredQuantity.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "measuredQuantity must be a number", data = null))
        }

        if(measuredEnergy.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "measuredEnergy must be a number", data = null))
        }

        if(measuredPcs.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "measuredPcs must be a number", data = null))
        }

        if(measuredPci.isNaN()){
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "measuredPci must be a number", data = null))
        }

        return try {
            val report = proxy.startTrackedFlow(::IssuerReport, issueReportpojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Transaction id ${report.linearId.id} committed to ledger.\n", data = report))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

    /***
     *
     * Update ReportState
     *
     */
    @PostMapping(value = [ "update-report" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateReport(
            @RequestBody
            updateReportPojo: ReportUpdatePojo): ResponseEntity<ResponsePojo> {

        val updateReportID = updateReportPojo.reportID
        val updateMeasuredQuantity = updateReportPojo.measuredQuantity
        val updateMeasuredEnergy = updateReportPojo.measuredEnergy
        val updateMeasuredPcs = updateReportPojo.measuredPcs
        val updateMeasuredPci = updateReportPojo.measuredPci

        if(updateReportID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "reportID cannot be empty", data = null))
        }

        if(updateMeasuredQuantity.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "measuredQuantity cannot be empty", data = null))
        }

        if(updateMeasuredEnergy.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "updateMeasuredEnergy cannot be empty", data = null))
        }

        if(updateMeasuredPcs.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "measuredPcs cannot be empty", data = null))
        }

        if(updateMeasuredPci.isNaN()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "measuredPci cannot be empty", data = null))
        }

        return try {
            val updateReportState = proxy.startTrackedFlow(::UpdaterReportState, updateReportPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Report with id: $updateReportID update correctly. New ReportState with id: ${updateReportState.linearId.id} created.. ledger updated.", data = updateReportState))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }
}
