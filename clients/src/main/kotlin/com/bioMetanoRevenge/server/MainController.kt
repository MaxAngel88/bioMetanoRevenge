package com.bioMetanoRevenge.server

import com.bioMetanoRevenge.flow.BatchFlow.IssuerBatch
import com.bioMetanoRevenge.flow.BatchFlow.UpdaterBatch
import com.bioMetanoRevenge.flow.EnrollFlow.IssuerEnroll
import com.bioMetanoRevenge.flow.EnrollFlow.UpdaterEnroll
import com.bioMetanoRevenge.flow.ProgrammingFlow.IssuerProgramming
import com.bioMetanoRevenge.flow.ProgrammingFlow.UpdaterProgramming
import com.bioMetanoRevenge.flow.RawMaterialFlow.IssuerRawMaterial
import com.bioMetanoRevenge.schema.BatchSchemaV1
import com.bioMetanoRevenge.schema.EnrollSchemaV1
import com.bioMetanoRevenge.schema.ProgrammingSchemaV1
import com.bioMetanoRevenge.schema.RawMaterialSchemaV1
import com.bioMetanoRevenge.state.BatchState
import com.bioMetanoRevenge.state.EnrollState
import com.bioMetanoRevenge.state.ProgrammingState
import com.bioMetanoRevenge.state.RawMaterialState
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

        // setting the criteria for retrive UNCONSUMED state AND filter it for hostname
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

        // setting the criteria for retrive UNCONSUMED state AND filter it for macAddress
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

        // setting the criteria for retrive UNCONSUMED state AND filter it for hostname
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

        // setting the criteria for retrive UNCONSUMED state AND filter it for hostname
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

        // setting the criteria for retrive UNCONSUMED state AND filter it for hostname
        var batchStatusCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {BatchSchemaV1.PersistentBatch::batchStatus.equal(batchStatus)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(EnrollState::class.java))

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

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for producer organization name
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

        // setting the criteria for retrive CONSUMED - UNCONSUMED state AND filter it for producer organization name and month
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
}
