package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.ProgrammingContract
import com.bioMetanoRevenge.schema.ProgrammingSchemaV1
import com.bioMetanoRevenge.state.ProgrammingState
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
import pojo.ProgrammingPojo
import pojo.ProgrammingUpdateDocPojo
import pojo.ProgrammingUpdateStatusPojo
import java.time.Instant
import java.util.*

object ProgrammingFlow {

    /**
     *
     * Issue Programming Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerProgramming(val programmingProperty: ProgrammingPojo) : FlowLogic<ProgrammingState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Programming.")
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
        override fun call(): ProgrammingState {

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

            val produttore: Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Generate an unsigned transaction.
            val programmingState = ProgrammingState(
                    GSEParty,
                    produttore,
                    programmingProperty.sendDate,
                    programmingProperty.monthYear,
                    programmingProperty.programmingType,
                    programmingProperty.versionFile,
                    programmingProperty.bioAgreementCode,
                    programmingProperty.remiCode,
                    programmingProperty.docRef,
                    programmingProperty.docName,
                    programmingProperty.programmingStatus,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(ProgrammingContract.Commands.Issue(), programmingState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(programmingState, ProgrammingContract.ID)
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

            return programmingState
        }
    }

    @InitiatedBy(IssuerProgramming::class)
    class ReceiverProgramming(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an programming transaction." using (output is ProgrammingState)
                    val programming = output as ProgrammingState
                    /* "other rule programming" using (programming is new rule) */
                    "bioAgreementCode cannot be empty" using (programming.bioAgreementCode.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Programming Doc Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterProgrammingDoc(val programmingUpdateDocProperty: ProgrammingUpdateDocPojo) : FlowLogic<ProgrammingState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update Programming Doc.")
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
        override fun call(): ProgrammingState {

            // Obtain a reference from a notary we wish to use.
            /**
             *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
            // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

            val produttore: Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for bioAgreementCode
            var bioAgreementCodeCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { ProgrammingSchemaV1.PersistentProgramming::bioAgreementCode.equal(programmingUpdateDocProperty.bioAgreementCode) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ProgrammingState::class.java))

            val oldProgrammingStateList = serviceHub.vaultService.queryBy<ProgrammingState>(
                    bioAgreementCodeCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldProgrammingStateList.size > 1 || oldProgrammingStateList.isEmpty()) throw FlowException("No programming state with bioAgreementCode: ${programmingUpdateDocProperty.bioAgreementCode} found.")

            val oldProgrammingStateRef = oldProgrammingStateList[0]
            val oldProgrammingState = oldProgrammingStateRef.state.data

            // Generate an unsigned transaction.
            val newProgrammingState = ProgrammingState(
                    GSEParty,
                    produttore,
                    oldProgrammingState.sendDate,
                    oldProgrammingState.monthYear,
                    "RET",
                    programmingUpdateDocProperty.versionFile,
                    oldProgrammingState.bioAgreementCode,
                    oldProgrammingState.remiCode,
                    programmingUpdateDocProperty.docRef,
                    programmingUpdateDocProperty.docName,
                    oldProgrammingState.programmingStatus,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(ProgrammingContract.Commands.UpdateDoc(), newProgrammingState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldProgrammingStateRef)
                    .addOutputState(newProgrammingState, ProgrammingContract.ID)
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

            return newProgrammingState
        }
    }

    @InitiatedBy(UpdaterProgrammingDoc::class)
    class UpdateAcceptorProgrammingDoc(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an programming transaction." using (output is ProgrammingState)
                    val programming = output as ProgrammingState
                    /* "other rule programming" using (output is new rule) */
                    "bioAgreementCode cannot be empty on update" using (programming.bioAgreementCode.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Programming Status Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterProgrammingStatus(val programmingUpdateStatusProperty: ProgrammingUpdateStatusPojo) : FlowLogic<ProgrammingState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update Programming Status.")
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
        override fun call(): ProgrammingState {

            // Obtain a reference from a notary we wish to use.
            /**
             *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
            // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

            val produttore: Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for bioAgreementCode
            var bioAgreementCodeCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { ProgrammingSchemaV1.PersistentProgramming::bioAgreementCode.equal(programmingUpdateStatusProperty.bioAgreementCode) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ProgrammingState::class.java))

            val oldProgrammingStateList = serviceHub.vaultService.queryBy<ProgrammingState>(
                    bioAgreementCodeCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldProgrammingStateList.size > 1 || oldProgrammingStateList.isEmpty()) throw FlowException("No programming state with bioAgreementCode: ${programmingUpdateStatusProperty.bioAgreementCode} found.")

            val oldProgrammingStateRef = oldProgrammingStateList[0]
            val oldProgrammingState = oldProgrammingStateRef.state.data

            // Generate an unsigned transaction.
            val newProgrammingState = ProgrammingState(
                    GSEParty,
                    produttore,
                    oldProgrammingState.sendDate,
                    oldProgrammingState.monthYear,
                    oldProgrammingState.programmingType,
                    oldProgrammingState.versionFile,
                    oldProgrammingState.bioAgreementCode,
                    oldProgrammingState.remiCode,
                    oldProgrammingState.docRef,
                    oldProgrammingState.docName,
                    programmingUpdateStatusProperty.programmingStatus,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(ProgrammingContract.Commands.UpdateStatus(), newProgrammingState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldProgrammingStateRef)
                    .addOutputState(newProgrammingState, ProgrammingContract.ID)
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

            return newProgrammingState
        }
    }

    @InitiatedBy(UpdaterProgrammingStatus::class)
    class UpdateAcceptorProgrammingStatus(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an programming transaction." using (output is ProgrammingState)
                    val programming = output as ProgrammingState
                    /* "other rule programming" using (output is new rule) */
                    "bioAgreementCode cannot be empty on update" using (programming.bioAgreementCode.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
