package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.BatchContract
import com.bioMetanoRevenge.schema.BatchSchemaV1
import com.bioMetanoRevenge.state.BatchState
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
import pojo.BatchPojo
import pojo.BatchUpdateAuctionPojo
import pojo.BatchUpdatePojo
import java.time.Instant
import java.util.*

object BatchFlow {

    /**
     *
     * Issue Batch Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerBatch(val batchProperty: BatchPojo) : FlowLogic<BatchState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Batch.")
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
        override fun call(): BatchState {

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
             * This flow can only be started from the GSE node
             *
             */
            val myLegalIdentity: Party = serviceHub.myInfo.legalIdentities.first()
            var myLegalIdentityNameOrg: String = myLegalIdentity.name.organisation

            if (myLegalIdentityNameOrg != "GSE") {
                throw FlowException("Node $myLegalIdentity cannot start the issue-batch flow. This flow can only be started from the GSE node")
            }

            // set Snam Party value
            var snamX500Name = CordaX500Name(organisation = "Snam", locality = "Milan", country = "IT")
            var snamParty = serviceHub.networkMapCache.getPeerByLegalName(snamX500Name)!!

            // set Party value for Produttore
            var produttoreX500Name = CordaX500Name(organisation = batchProperty.produttore, locality = "Milan", country = "IT")
            var produttoreParty = serviceHub.networkMapCache.getPeerByLegalName(produttoreX500Name)!!

            // set Party value for Counterpart
            var counterpartX500Name = CordaX500Name(organisation = batchProperty.counterpart, locality = "Milan", country = "IT")
            var counterpartParty = serviceHub.networkMapCache.getPeerByLegalName(counterpartX500Name)!!

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val batchState = BatchState(
                    myLegalIdentity,
                    snamParty,
                    produttoreParty,
                    counterpartParty,
                    batchProperty.idProducer,
                    batchProperty.idCounterpart,
                    batchProperty.transactionType,
                    batchProperty.batchID,
                    batchProperty.month,
                    batchProperty.remiCode,
                    batchProperty.plantAddress,
                    batchProperty.plantCode,
                    batchProperty.initialQuantity,
                    batchProperty.quantity,
                    batchProperty.energy,
                    batchProperty.price,
                    0.0,
                    batchProperty.averageProductionCapacity,
                    batchProperty.maxProductionCapacity,
                    batchProperty.annualEstimate,
                    batchProperty.pcs,
                    batchProperty.pci,
                    batchProperty.startingPosition,
                    batchProperty.arrivalPosition,
                    batchProperty.docRef,
                    batchProperty.docDate,
                    batchProperty.batchStatus,
                    batchProperty.supportField,
                    batchProperty.auctionStatus,
                    batchProperty.batchDate,
                    batchProperty.batchDate,
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(BatchContract.Commands.Issue(), batchState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(batchState, BatchContract.ID)
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

            // Send the state to the other nodes, and receive it back with their signature.
            val snamSession = initiateFlow(snamParty)
            val produttoreSession = initiateFlow(produttoreParty)
            val counterpartSession = initiateFlow(counterpartParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(snamSession, produttoreSession, counterpartSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(snamSession, produttoreSession, counterpartSession), FINALISING_TRANSACTION.childProgressTracker()))

            return batchState
        }
    }

    @InitiatedBy(IssuerBatch::class)
    class ReceiverBatch(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an batch transaction." using (output is BatchState)
                    val batchState = output as BatchState
                    /* "other rule batch" using (batch is new rule) */
                    "batchID cannot be empty" using (batchState.batchID.isNotEmpty())
                    "idProducer cannot be empty" using (batchState.idProducer.isNotEmpty())
                    "idCounterpart cannot be empty" using (batchState.idCounterpart.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Batch Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterBatch(val batchUpdateProperty: BatchUpdatePojo) : FlowLogic<BatchState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update Batch.")
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
        override fun call(): BatchState {

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
             * This flow can only be started from the GSE node
             *
             */
            val myLegalIdentity: Party = serviceHub.myInfo.legalIdentities.first()
            var myLegalIdentityNameOrg: String = myLegalIdentity.name.organisation

            if (myLegalIdentityNameOrg != "GSE") {
                throw FlowException("Node $myLegalIdentity cannot start the update-batch flow. This flow can only be started from the GSE node")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for batchID
            var batchIDCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { BatchSchemaV1.PersistentBatch::batchID.equal(batchUpdateProperty.batchID) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

            val oldBatchStateList = serviceHub.vaultService.queryBy<BatchState>(
                    batchIDCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldBatchStateList.size > 1 || oldBatchStateList.isEmpty()) throw FlowException("No batch state with batchID: ${batchUpdateProperty.batchID} found.")

            val oldBatchStateRef = oldBatchStateList[0]
            val oldBatchState = oldBatchStateRef.state.data

            // Generate an unsigned transaction.
            val newBatchState = BatchState(
                    myLegalIdentity,
                    oldBatchState.Snam,
                    oldBatchState.produttore,
                    oldBatchState.counterpart,
                    oldBatchState.idProducer,
                    oldBatchState.idCounterpart,
                    oldBatchState.transactionType,
                    oldBatchState.batchID,
                    oldBatchState.month,
                    oldBatchState.remiCode,
                    oldBatchState.plantAddress,
                    oldBatchState.plantCode,
                    oldBatchState.initialQuantity,
                    oldBatchState.quantity - batchUpdateProperty.sellingQuantity,
                    oldBatchState.energy,
                    oldBatchState.price,
                    batchUpdateProperty.sellingPrice,
                    oldBatchState.averageProductionCapacity,
                    oldBatchState.maxProductionCapacity,
                    oldBatchState.annualEstimate,
                    oldBatchState.pcs,
                    oldBatchState.pci,
                    oldBatchState.startingPosition,
                    oldBatchState.arrivalPosition,
                    oldBatchState.docRef,
                    oldBatchState.docDate,
                    batchUpdateProperty.batchStatus,
                    oldBatchState.supportField,
                    oldBatchState.auctionStatus,
                    oldBatchState.batchDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(BatchContract.Commands.Update(), newBatchState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldBatchStateRef)
                    .addOutputState(newBatchState, BatchContract.ID)
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

            // Send the state to the other nodes, and receive it back with their signature.
            val snamSession = initiateFlow(oldBatchState.Snam)
            val produttoreSession = initiateFlow(oldBatchState.produttore)
            val counterpartSession = initiateFlow(oldBatchState.counterpart)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(snamSession, produttoreSession, counterpartSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(snamSession, produttoreSession, counterpartSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newBatchState
        }
    }

    @InitiatedBy(UpdaterBatch::class)
    class UpdateAcceptorBatch(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an batch transaction." using (output is BatchState)
                    val batchState = output as BatchState
                    /* "other rule batch" using (output is new rule) */
                    "batchID cannot be empty" using (batchState.batchID.isNotEmpty())
                    "quantity must be greater or equal than zero" using (batchState.quantity >= 0.0)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Batch Auction Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterBatchAuction(val batchUpdateAuctionProperty: BatchUpdateAuctionPojo) : FlowLogic<BatchState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update Batch Auction.")
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
        override fun call(): BatchState {

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
             * This flow can only be started from the GSE node
             *
             */
            val myLegalIdentity: Party = serviceHub.myInfo.legalIdentities.first()
            var myLegalIdentityNameOrg: String = myLegalIdentity.name.organisation

            if (myLegalIdentityNameOrg != "GSE") {
                throw FlowException("Node $myLegalIdentity cannot start the update-batch flow. This flow can only be started from the GSE node")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for batchID
            var batchIDCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { BatchSchemaV1.PersistentBatch::batchID.equal(batchUpdateAuctionProperty.batchID) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(BatchState::class.java))

            val oldBatchStateList = serviceHub.vaultService.queryBy<BatchState>(
                    batchIDCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldBatchStateList.size > 1 || oldBatchStateList.isEmpty()) throw FlowException("No batch state with batchID: ${batchUpdateAuctionProperty.batchID} found.")

            val oldBatchStateRef = oldBatchStateList[0]
            val oldBatchState = oldBatchStateRef.state.data

            // Generate an unsigned transaction.
            val newBatchState = BatchState(
                    myLegalIdentity,
                    oldBatchState.Snam,
                    oldBatchState.produttore,
                    oldBatchState.counterpart,
                    oldBatchState.idProducer,
                    oldBatchState.idCounterpart,
                    oldBatchState.transactionType,
                    oldBatchState.batchID,
                    oldBatchState.month,
                    oldBatchState.remiCode,
                    oldBatchState.plantAddress,
                    oldBatchState.plantCode,
                    oldBatchState.initialQuantity,
                    oldBatchState.quantity,
                    oldBatchState.energy,
                    oldBatchState.price,
                    oldBatchState.sellingPrice,
                    oldBatchState.averageProductionCapacity,
                    oldBatchState.maxProductionCapacity,
                    oldBatchState.annualEstimate,
                    oldBatchState.pcs,
                    oldBatchState.pci,
                    oldBatchState.startingPosition,
                    oldBatchState.arrivalPosition,
                    oldBatchState.docRef,
                    oldBatchState.docDate,
                    oldBatchState.batchStatus,
                    batchUpdateAuctionProperty.supportField,
                    batchUpdateAuctionProperty.auctionStatus,
                    oldBatchState.batchDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(BatchContract.Commands.UpdateAuction(), newBatchState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldBatchStateRef)
                    .addOutputState(newBatchState, BatchContract.ID)
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

            // Send the state to the other nodes, and receive it back with their signature.
            val snamSession = initiateFlow(oldBatchState.Snam)
            val produttoreSession = initiateFlow(oldBatchState.produttore)
            val counterpartSession = initiateFlow(oldBatchState.counterpart)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(snamSession, produttoreSession, counterpartSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(snamSession, produttoreSession, counterpartSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newBatchState
        }
    }

    @InitiatedBy(UpdaterBatchAuction::class)
    class UpdateAuctionAcceptorBatch(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an batch transaction." using (output is BatchState)
                    val batchState = output as BatchState
                    /* "other rule batch" using (output is new rule) */
                    "batchID cannot be empty" using (batchState.batchID.isNotEmpty())
                    "supportField cannot be empty" using (batchState.supportField.isNotEmpty())
                    "auctionStatus cannot be empty" using (batchState.auctionStatus.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
