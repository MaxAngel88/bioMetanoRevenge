package com.bioMetanoRevenge.server

import com.bioMetanoRevenge.flow.BatchFlow.IssuerBatch
import com.bioMetanoRevenge.flow.BatchFlow.UpdaterBatch
import com.bioMetanoRevenge.flow.EnrollFlow.IssuerEnroll
import com.bioMetanoRevenge.flow.EnrollFlow.UpdaterEnroll
import com.bioMetanoRevenge.flow.ExchangeFlow.IssuerExchange
import com.bioMetanoRevenge.flow.ExchangeFlow.UpdaterExchange
import com.bioMetanoRevenge.flow.PSVFlow.IssuerPSVState
import com.bioMetanoRevenge.flow.PSVFlow.UpdaterPSVState
import com.bioMetanoRevenge.flow.ProgrammingFlow.IssuerProgramming
import com.bioMetanoRevenge.flow.ProgrammingFlow.UpdaterProgramming
import com.bioMetanoRevenge.flow.RawMaterialFlow.IssuerRawMaterial
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
     * Update Programming
     *
     */
    @PostMapping(value = [ "update-programming" ], consumes = [APPLICATION_JSON_VALUE], produces = [ APPLICATION_JSON_VALUE], headers = [ "Content-Type=application/json" ])
    fun updateProgramming(
            @RequestBody
            updateProgrammingPojo: ProgrammingUpdatePojo): ResponseEntity<ResponsePojo> {

        val versionFile = updateProgrammingPojo.versionFile
        val bioAgreementCode = updateProgrammingPojo.bioAgreementCode
        val docRef = updateProgrammingPojo.docRef
        val docName = updateProgrammingPojo.docName

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
            val updateProgramming = proxy.startTrackedFlow(::UpdaterProgramming, updateProgrammingPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Programming with bioAgreementCode: $bioAgreementCode update correctly." + "New ProgrammingState with id: ${updateProgramming.linearId.id} created.. ledger updated.", data = updateProgramming))
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
     * Displays last BatchState that exist in the node's vault for selected shipper (organization name).
     */
    @GetMapping(value = [ "getLastBatchStateByShipper/{shipperName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getLastBatchStateByShipper(
            @PathVariable("shipperName")
            shipperName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive UNCONSUMED state AND filter it for shipper organization name
        val generalUnconsumedStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

        val foundShipperBatch = proxy.vaultQueryBy<BatchState>(
                generalUnconsumedStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.shipper.name.organisation == shipperName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "Last BatchState by shipper $shipperName .", data = foundShipperBatch))
    }

    /**
     * Displays History BatchState that exist in the node's vault for selected shipper (organization name).
     */
    @GetMapping(value = [ "getHistoryBatchStateByShipper/{shipperName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByShipper(
            @PathVariable("shipperName")
            shipperName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for shipper organization name
        val generalAllStateCriteria : QueryCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundShipperBatchHistory = proxy.vaultQueryBy<BatchState>(
                generalAllStateCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.shipper.name.organisation == shipperName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $shipperName", data = foundShipperBatchHistory))
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
     * Displays History BatchState that exist in the node's vault for selected shipper (organization name) and month.
     */
    @GetMapping(value = [ "getHistoryBatchStateByMonthShipper/{month}/{shipperName}" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getHistoryBatchStateByMonthShipper(
            @PathVariable("month")
            month : String,
            @PathVariable("shipperName")
            shipperName : String ) : ResponseEntity<ResponsePojo> {

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for shipper organization name and month
        var monthCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::month.equal(month)}, status = Vault.StateStatus.ALL, contractStateTypes = setOf(BatchState::class.java))

        val foundMonthShipperBatchHistory = proxy.vaultQueryBy<BatchState>(
                monthCriteria,
                PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = 4000),
                Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
        ).states.filter { it.state.data.shipper.name.organisation == shipperName }

        return ResponseEntity.status(HttpStatus.OK).body(ResponsePojo(outcome = "SUCCESS", message = "History of batch state for $shipperName in $month.", data = foundMonthShipperBatchHistory))
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
        val shipper = issueBatchPojo.shipper
        val batchID = issueBatchPojo.batchID
        val month = issueBatchPojo.month

        if(produttore.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "produttore (organization name) cannot be empty", data = null))
        }

        if(shipper.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "shipper (organization name) cannot be empty", data = null))
        }

        if(batchID.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchID cannot be empty", data = null))
        }

        if(month.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "month cannot be empty", data = null))
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

        if(batchId.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchId cannot be empty", data = null))
        }

        if(batchStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "batchStatus cannot be empty", data = null))
        }

        return try {
            val updateBatch = proxy.startTrackedFlow(::UpdaterBatch, updateBatchPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Batch with id: $batchId update correctly. New BatchState with id: ${updateBatch.linearId.id} created.. ledger updated.", data = updateBatch))
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

        if(exchangeCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "exchangeCode cannot be empty", data = null))
        }

        if(exchangeStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "exchangeStatus cannot be empty", data = null))
        }

        return try {
            val updateExchange = proxy.startTrackedFlow(::UpdaterExchange, updateExchangePojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Exchange with id: $exchangeCode update correctly. New ExchangeState with id: ${updateExchange.linearId.id} created.. ledger updated.", data = updateExchange))
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

        if(transactionCode.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "transactionCode cannot be empty", data = null))
        }

        if(transactionStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = "transactionStatus cannot be empty", data = null))
        }

        return try {
            val updatePSVState = proxy.startTrackedFlow(::UpdaterPSVState, updatePSVPojo).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "PSV with id: $transactionCode update correctly. New PSVState with id: ${updatePSVState.linearId.id} created.. ledger updated.", data = updatePSVState))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }
}
