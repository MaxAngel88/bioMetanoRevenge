package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.ReportContract
import com.bioMetanoRevenge.schema.ReportSchemaV1
import com.bioMetanoRevenge.state.ReportState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import pojo.ReportPojo
import pojo.ReportUpdatePojo
import java.time.Instant
import java.util.*

object ReportFlow {

    /**
     *
     * Issue ReportState Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerReport(val reportProperty: ReportPojo) : FlowLogic<ReportState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Report.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the other Party signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): ReportState {

            // Obtain a reference from a notary we wish to use.
            /**
             *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
            // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            /**
             *
             * This flow can only be started from the Snam node
             *
             */
            val myLegalIdentity: Party = serviceHub.myInfo.legalIdentities.first()
            var myLegalIdentityNameOrg: String = myLegalIdentity.name.organisation

            if (myLegalIdentityNameOrg != "Snam") {
                throw FlowException("Node $myLegalIdentity cannot start the issue-report flow. This flow can only be started from the Snam node")
            }

            val Snam: Party = myLegalIdentity

            // fixed Party value for GSE
            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // set Party value for owner
            var ownerX500Name = CordaX500Name(organisation = reportProperty.ownerNode, locality = "Milan", country = "IT")
            var ownerParty = serviceHub.networkMapCache.getPeerByLegalName(ownerX500Name)!!

            // Generate an unsigned transaction.
            val reportState = ReportState(
                    GSEParty,
                    Snam,
                    ownerParty,
                    reportProperty.ownerID,
                    reportProperty.reportID,
                    reportProperty.reportType,
                    reportProperty.remiCode,
                    reportProperty.remiAddress,
                    reportProperty.offerCode,
                    reportProperty.transactionCode,
                    reportProperty.operation,
                    reportProperty.measuredQuantity,
                    reportProperty.measuredEnergy,
                    reportProperty.measuredPcs,
                    reportProperty.measuredPci,
                    Instant.now(),
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(ReportContract.Commands.Issue(), reportState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(reportState, ReportContract.ID)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            // Send the state to the other node, and receive it back with their signature.
            val GSESession = initiateFlow(GSEParty)
            val ownerSession = initiateFlow(ownerParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(GSESession, ownerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(GSESession, ownerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return reportState
        }
    }

    @InitiatedBy(IssuerReport::class)
    class ReceiverReport(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an ReportState transaction." using (output is ReportState)
                    val reportState = output as ReportState
                    /* "other rule reportState" using (reportState is new rule) */
                    "reportID cannot be empty" using (reportState.reportID.isNotEmpty())
                    "ownerID cannot be empty" using (reportState.ownerID.isNotEmpty())
                    "remiCode cannot be empty" using (reportState.remiCode.isNotEmpty())
                    "measuredQuantity must be a number" using (!reportState.measuredQuantity.isNaN())
                    "measuredEnergy must be a number" using (!reportState.measuredEnergy.isNaN())
                    "measuredPcs must be a number" using (!reportState.measuredPcs.isNaN())
                    "measuredPci must be a number" using (!reportState.measuredPci.isNaN())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update ReportState Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterReportState(val reportUpdateProperty: ReportUpdatePojo) : FlowLogic<ReportState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update ReportState.")
            object VERIFYIGN_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the other Party signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYIGN_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): ReportState {

            // Obtain a reference from a notary we wish to use.
            /**
             *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
            // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

            /**
             *
             * This flow can only be started from the Snam node
             *
             */
            val myLegalIdentity: Party = serviceHub.myInfo.legalIdentities.first()
            var myLegalIdentityNameOrg: String = myLegalIdentity.name.organisation

            if (myLegalIdentityNameOrg != "Snam") {
                throw FlowException("Node $myLegalIdentity cannot start the update-report flow. This flow can only be started from the Snam node")
            }

            val Snam: Party = myLegalIdentity

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for ID
            var reportIDCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { ReportSchemaV1.PersistentReport::reportID.equal(reportUpdateProperty.reportID) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ReportState::class.java))

            val oldReportStateList = serviceHub.vaultService.queryBy<ReportState>(
                    reportIDCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldReportStateList.size > 1 || oldReportStateList.isEmpty()) throw FlowException("No report state with ID: ${reportUpdateProperty.reportID} found.")

            val oldReportStateRef = oldReportStateList[0]
            val oldReportState = oldReportStateRef.state.data

            // Generate an unsigned transaction.
            val newReportState = ReportState(
                    oldReportState.GSE,
                    Snam,
                    oldReportState.owner,
                    oldReportState.ownerID,
                    oldReportState.reportID,
                    oldReportState.reportType,
                    oldReportState.remiCode,
                    oldReportState.remiAddress,
                    oldReportState.offerCode,
                    oldReportState.transactionCode,
                    oldReportState.operation,
                    reportUpdateProperty.measuredQuantity,
                    reportUpdateProperty.measuredEnergy,
                    reportUpdateProperty.measuredPcs,
                    reportUpdateProperty.measuredPci,
                    oldReportState.reportDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(ReportContract.Commands.Update(), newReportState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldReportStateRef)
                    .addOutputState(newReportState, ReportContract.ID)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYIGN_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS

            val GSESession = initiateFlow(oldReportState.GSE)
            val ownerSession = initiateFlow(oldReportState.owner)

            // Send the state to the counterparty, and receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(GSESession, ownerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(GSESession, ownerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newReportState
        }
    }

    @InitiatedBy(UpdaterReportState::class)
    class UpdateAcceptorReport(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an reportState transaction." using (output is ReportState)
                    val newReportState = output as ReportState
                    /* "other rule reportState" using (output is new rule) */
                    "reportID cannot be empty on update" using (newReportState.reportID.isNotEmpty())
                    "measuredQuantity must be a number on update" using (!newReportState.measuredQuantity.isNaN())
                    "measuredEnergy must be a number on update" using (!newReportState.measuredEnergy.isNaN())
                    "measuredPcs must be a number on update" using (!newReportState.measuredPcs.isNaN())
                    "measuredPci must be a number on update" using (!newReportState.measuredPci.isNaN())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
