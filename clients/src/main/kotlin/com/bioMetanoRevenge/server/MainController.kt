package com.bioMetanoRevenge.server

import com.bioMetanoRevenge.flow.EnrollFlow.IssuerEnroll
import com.bioMetanoRevenge.flow.EnrollFlow.UpdaterEnroll
import com.bioMetanoRevenge.schema.EnrollSchemaV1
import com.bioMetanoRevenge.state.EnrollState
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
import pojo.EnrollPojo
import pojo.EnrollUpdatePojo
import pojo.ResponsePojo

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
     * see it when calling /spring/riuscada.com/api/ on their respective nodes.
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
            ResponseEntity.status(HttpStatus.CREATED).body(ResponsePojo(outcome = "SUCCESS", message = "Enroll with id: $uuid update correctly." + "New EnrollState with id: ${updateEnroll.linearId.id} created.. ledger updated.", data = updateEnroll))
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ResponsePojo(outcome = "ERROR", message = ex.message!!, data = null))
        }
    }

}
