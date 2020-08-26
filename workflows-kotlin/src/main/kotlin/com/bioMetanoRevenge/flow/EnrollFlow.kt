package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.EnrollContract
import com.bioMetanoRevenge.schema.EnrollSchemaV1
import com.bioMetanoRevenge.state.EnrollState
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
import pojo.EnrollPojo
import pojo.EnrollUpdatePojo
import java.time.Instant
import java.util.*

object EnrollFlow {

    /**
     *
     * Issue Enroll Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerEnroll(val enrollProperty: EnrollPojo) : FlowLogic<EnrollState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Enroll.")
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
        override fun call(): EnrollState {

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

            val owner : Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Generate an unsigned transaction.
            val enrollState = EnrollState(
                    GSEParty,
                    owner,
                    enrollProperty.enrollType,
                    enrollProperty.businessName,
                    enrollProperty.PIVA,
                    enrollProperty.birthPlace,
                    enrollProperty.idPlant,
                    enrollProperty.plantAddress,
                    enrollProperty.username,
                    enrollProperty.role,
                    enrollProperty.partner,
                    enrollProperty.docRefAutodichiarazione,
                    enrollProperty.docRefAttestazioniTecniche,
                    enrollProperty.docDeadLine,
                    enrollProperty.enrollStatus,
                    Instant.now(),
                    0.0,
                    0.0,
                    enrollProperty.uuid,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(EnrollContract.Commands.Issue(), enrollState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(enrollState, EnrollContract.ID)
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

            return enrollState
        }
    }

    @InitiatedBy(IssuerEnroll::class)
    class ReceiverEnroll(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an enroll transaction." using (output is EnrollState)
                    val enroll = output as EnrollState
                    /* "other rule enroll" using (enroll is new rule) */
                    "uuid cannot be empty" using (enroll.uuid.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Measure Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterEnroll(val enrollUpdateProperty: EnrollUpdatePojo) : FlowLogic<EnrollState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update Enroll.")
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
        override fun call(): EnrollState {

            // Obtain a reference from a notary we wish to use.
            /**
             *  METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
             *
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            val notary = serviceHub.networkMapCache.notaryIdentities.single() // METHOD 1
            // val notary = serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2

            val owner : Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for uuid
            var uuidCriteria : QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder {EnrollSchemaV1.PersistentEnroll::uuid.equal(enrollUpdateProperty.uuid)}, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(EnrollState::class.java))

            val oldEnrollStateList = serviceHub.vaultService.queryBy<EnrollState>(
                    uuidCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldEnrollStateList.size > 1 || oldEnrollStateList.isEmpty()) throw FlowException("No enroll state with uuid: ${enrollUpdateProperty.uuid} found.")

            val oldEnrollStateRef = oldEnrollStateList[0]
            val oldEnrollState = oldEnrollStateRef.state.data

            // Generate an unsigned transaction.
            val newEnrollState = EnrollState(
                    GSEParty,
                    owner,
                    oldEnrollState.enrollType,
                    oldEnrollState.businessName,
                    oldEnrollState.PIVA,
                    oldEnrollState.birthPlace,
                    oldEnrollState.idPlant,
                    oldEnrollState.plantAddress,
                    oldEnrollState.username,
                    oldEnrollState.role,
                    oldEnrollState.partner,
                    oldEnrollState.docRefAutodichiarazione,
                    oldEnrollState.docRefAttestazioniTecniche,
                    oldEnrollState.docDeadLine,
                    enrollUpdateProperty.enrollStatus,
                    oldEnrollState.enrollDate,
                    enrollUpdateProperty.bioGasAmount,
                    enrollUpdateProperty.gasAmount,
                    oldEnrollState.uuid,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(EnrollContract.Commands.Update(), newEnrollState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldEnrollStateRef)
                    .addOutputState(newEnrollState, EnrollContract.ID)
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
            return newEnrollState
        }

        @InitiatedBy(UpdaterEnroll::class)
        class UpdateAcceptorEnroll(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val output = stx.tx.outputs.single().data
                        "This must be an enroll transaction." using (output is EnrollState)
                        val enroll = output as EnrollState
                        /* "other rule enroll" using (output is new rule) */
                        "uuid cannot be empty on update" using (enroll.uuid.isNotEmpty())
                    }
                }
                val txId = subFlow(signTransactionFlow).id

                return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))

            }
        }
    }
}