package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.InvoiceContract
import com.bioMetanoRevenge.schema.InvoiceSchemaV1
import com.bioMetanoRevenge.state.InvoiceState
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
import pojo.InvoicePojo
import pojo.InvoiceUpdateStatusPojo
import java.time.Instant
import java.util.*

object InvoiceFlow {

    /**
     *
     * Issue Invoice Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerInvoice(val invoiceProperty: InvoicePojo) : FlowLogic<InvoiceState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Invoice.")
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
        override fun call(): InvoiceState {

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

            val owner: Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Check if the invoiceID is already used for another issue Invoice
            var invoiceIDCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { InvoiceSchemaV1.PersistentInvoice::invoiceID.equal(invoiceProperty.invoiceID) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(InvoiceState::class.java))

            val oldInvoiceStateList = serviceHub.vaultService.queryBy<InvoiceState>(
                    invoiceIDCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldInvoiceStateList.isNotEmpty()) throw FlowException("invoiceID: ${invoiceProperty.invoiceID} already used for another state.. invoiceID must be unique.")

            // Generate an unsigned transaction.
            val invoiceState = InvoiceState(
                    GSEParty,
                    owner,
                    invoiceProperty.ownerID,
                    invoiceProperty.invoiceID,
                    invoiceProperty.invoiceRef,
                    invoiceProperty.parentQuadrioID,
                    invoiceProperty.unityPrice,
                    invoiceProperty.quantity,
                    Math.round(invoiceProperty.unityPrice * invoiceProperty.quantity * 100.0) / 100.0,
                    invoiceProperty.productType,
                    invoiceProperty.invoiceStatus,
                    Instant.now(),
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(InvoiceContract.Commands.Issue(), invoiceState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(invoiceState, InvoiceContract.ID)
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
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(GSESession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(GSESession), FINALISING_TRANSACTION.childProgressTracker()))

            return invoiceState
        }
    }

    @InitiatedBy(IssuerInvoice::class)
    class ReceiverInvoice(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an invoice transaction." using (output is InvoiceState)
                    val invoiceState = output as InvoiceState
                    /* "other rule invoice" using (invoice is new rule) */
                    "invoiceID cannot be empty" using (invoiceState.invoiceID.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Invoice Status Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterInvoiceStatus(val invoiceUpdateStatusProperty: InvoiceUpdateStatusPojo) : FlowLogic<InvoiceState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update status Invoice.")
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
        override fun call(): InvoiceState {

            // Obtain a reference from a notary we wish to use.
            /**
             *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
            // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

            val ownerParty: Party = serviceHub.myInfo.legalIdentities.first()

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for invoiceID
            var invoiceIDCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { InvoiceSchemaV1.PersistentInvoice::invoiceID.equal(invoiceUpdateStatusProperty.invoiceID) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(InvoiceState::class.java))

            val oldInvoiceStateList = serviceHub.vaultService.queryBy<InvoiceState>(
                    invoiceIDCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldInvoiceStateList.size > 1 || oldInvoiceStateList.isEmpty()) throw FlowException("No invoice state with invoiceID: ${invoiceUpdateStatusProperty.invoiceID} found.")

            val oldInvoiceStateRef = oldInvoiceStateList[0]
            val oldInvoiceState = oldInvoiceStateRef.state.data

            // Generate an unsigned transaction.
            val newInvoiceState = InvoiceState(
                    oldInvoiceState.GSE,
                    ownerParty,
                    oldInvoiceState.ownerID,
                    oldInvoiceState.invoiceID,
                    oldInvoiceState.invoiceRef,
                    oldInvoiceState.parentQuadrioID,
                    oldInvoiceState.unityPrice,
                    oldInvoiceState.quantity,
                    oldInvoiceState.totalPrice,
                    oldInvoiceState.productType,
                    invoiceUpdateStatusProperty.invoiceStatus,
                    oldInvoiceState.invoiceDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(InvoiceContract.Commands.UpdateStatus(), newInvoiceState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldInvoiceStateRef)
                    .addOutputState(newInvoiceState, InvoiceContract.ID)
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

            val GSESession = initiateFlow(oldInvoiceState.GSE)

            // Send the state to the counterparty, and receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(GSESession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(GSESession), FINALISING_TRANSACTION.childProgressTracker()))

            return newInvoiceState
        }
    }

    @InitiatedBy(UpdaterInvoiceStatus::class)
    class UpdateAcceptorInvoiceStatus(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an invoice transaction." using (output is InvoiceState)
                    val invoice = output as InvoiceState
                    /* "other rule invoice" using (invoice is new rule) */
                    "invoiceID cannot be empty on update" using (invoice.invoiceID.isNotEmpty())
                    "invoiceStatus cannot be empty on update" using (invoice.invoiceStatus.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
