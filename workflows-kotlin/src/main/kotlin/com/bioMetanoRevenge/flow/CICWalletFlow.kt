package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.CICWalletContract
import com.bioMetanoRevenge.schema.CICWalletSchemaV1
import com.bioMetanoRevenge.state.CICWalletState
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
import pojo.CICWalletPojo
import pojo.CICWalletUpdatePojo
import java.time.Instant
import java.util.*

object CICWalletFlow {

    /**
     *
     * Issue CICWallet Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerCICWallet(val cicWalletProperty: CICWalletPojo) : FlowLogic<CICWalletState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new CICWallet.")
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
        override fun call(): CICWalletState {

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

            // Generate an unsigned transaction.
            val cicWalletState = CICWalletState(
                    GSEParty,
                    owner,
                    cicWalletProperty.CICWalletID,
                    cicWalletProperty.CICAmount,
                    Instant.now(),
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(CICWalletContract.Commands.Issue(), cicWalletState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(cicWalletState, CICWalletContract.ID)
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

            return cicWalletState
        }
    }

    @InitiatedBy(IssuerCICWallet::class)
    class ReceiverCICWallet(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an CICWallet transaction." using (output is CICWalletState)
                    val cicWalletState = output as CICWalletState
                    /* "other rule CICWallet" using (CICWallet is new rule) */
                    "CICWalletID cannot be empty" using (cicWalletState.CICWalletID.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update CICWallet Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterCICWallet(val cicWalletUpdateProperty: CICWalletUpdatePojo) : FlowLogic<CICWalletState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update CICWallet.")
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
        override fun call(): CICWalletState {

            // Obtain a reference from a notary we wish to use.
            /**
             *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
            // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

            val owner: Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for uuid
            var cicWalletIDCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { CICWalletSchemaV1.PersistentCICWallet::CICWalletID.equal(cicWalletUpdateProperty.CICWalletID) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(CICWalletState::class.java))

            val oldCICWalletStateList = serviceHub.vaultService.queryBy<CICWalletState>(
                    cicWalletIDCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldCICWalletStateList.size > 1 || oldCICWalletStateList.isEmpty()) throw FlowException("No CICWallet state with CICWalletID: ${cicWalletUpdateProperty.CICWalletID} found.")

            val oldCICWalletStateRef = oldCICWalletStateList[0]
            val oldCICWalletState = oldCICWalletStateRef.state.data

            // Generate an unsigned transaction.
            val newCICWalletState = CICWalletState(
                    GSEParty,
                    owner,
                    oldCICWalletState.CICWalletID,
                    oldCICWalletState.CICAmount + cicWalletUpdateProperty.CICAddAmount,
                    oldCICWalletState.CICWalletDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(CICWalletContract.Commands.Update(), newCICWalletState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldCICWalletStateRef)
                    .addOutputState(newCICWalletState, CICWalletContract.ID)
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

            val GSESession = initiateFlow(GSEParty)

            // Send the state to the counterparty, and receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(GSESession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(GSESession), FINALISING_TRANSACTION.childProgressTracker()))

            return newCICWalletState
        }
    }

    @InitiatedBy(UpdaterCICWallet::class)
    class UpdateAcceptorCICWallet(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an CICWallet transaction." using (output is CICWalletState)
                    val cicWalletState = output as CICWalletState
                    /* "other rule CICWallet" using (output is new rule) */
                    "CICWalletID cannot be empty on update" using (cicWalletState.CICWalletID.isNotEmpty())
                    "CICAmount must be greater or equal than zero on update" using (cicWalletState.CICAmount >= 0.0)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
