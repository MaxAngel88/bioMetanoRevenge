package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.AgreementContract
import com.bioMetanoRevenge.schema.AgreementSchemaV1
import com.bioMetanoRevenge.state.AgreementState
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
import pojo.AgreementPojo
import pojo.AgreementUpdatePojo
import java.time.Instant
import java.util.*

object AgreementFlow {

    /**
     *
     * Issue Agreement Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerAgreement(val agreementProperty: AgreementPojo) : FlowLogic<AgreementState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Agreement.")
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
        override fun call(): AgreementState {

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

            val ownerParty: Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            var counterpartX500Name = CordaX500Name(organisation = agreementProperty.counterpartParty, locality = "Milan", country = "IT")
            var counterpartParty = serviceHub.networkMapCache.getPeerByLegalName(counterpartX500Name)!!

            // Generate an unsigned transaction.
            val agreementState = AgreementState(
                    GSEParty,
                    ownerParty,
                    counterpartParty,
                    agreementProperty.agreementID,
                    agreementProperty.agreementCode,
                    agreementProperty.agreementDescription,
                    agreementProperty.agreementType,
                    agreementProperty.agreementSubType,
                    agreementProperty.owner,
                    agreementProperty.counterpart,
                    agreementProperty.tso,
                    agreementProperty.portfolio,
                    agreementProperty.sourceSystem,
                    agreementProperty.programmingMethod,
                    agreementProperty.agreementNote,
                    agreementProperty.energy,
                    agreementProperty.dcq,
                    agreementProperty.price,
                    agreementProperty.validFrom,
                    agreementProperty.validTo,
                    Instant.now(),
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(AgreementContract.Commands.Issue(), agreementState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(agreementState, AgreementContract.ID)
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
            val counterpartSession = initiateFlow(counterpartParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(GSESession, counterpartSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(GSESession, counterpartSession), FINALISING_TRANSACTION.childProgressTracker()))

            return agreementState
        }
    }

    @InitiatedBy(IssuerAgreement::class)
    class ReceiverAgreement(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an agreement transaction." using (output is AgreementState)
                    val agreement = output as AgreementState
                    /* "other rule agreement" using (agreement is new rule) */
                    "agreementID cannot be empty" using (agreement.agreementID.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Agreement Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterAgreement(val agreementUpdateProperty: AgreementUpdatePojo) : FlowLogic<AgreementState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update Agreement.")
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
        override fun call(): AgreementState {

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

            // setting the criteria for retrive UNCONSUMED state AND filter it for agreementID
            var agreementIDCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { AgreementSchemaV1.PersistentAgreement::agreementID.equal(agreementUpdateProperty.agreementID) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(AgreementState::class.java))

            val oldAgreementStateList = serviceHub.vaultService.queryBy<AgreementState>(
                    agreementIDCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldAgreementStateList.size > 1 || oldAgreementStateList.isEmpty()) throw FlowException("No agreement state with agreementID: ${agreementUpdateProperty.agreementID} found.")

            val oldAgreementStateRef = oldAgreementStateList[0]
            val oldAgreementState = oldAgreementStateRef.state.data

            // Generate an unsigned transaction.
            val newAgreementState = AgreementState(
                    oldAgreementState.GSE,
                    ownerParty,
                    oldAgreementState.counterpartParty,
                    oldAgreementState.agreementID,
                    oldAgreementState.agreementCode,
                    oldAgreementState.agreementDescription,
                    oldAgreementState.agreementType,
                    oldAgreementState.agreementSubType,
                    oldAgreementState.owner,
                    oldAgreementState.counterpart,
                    oldAgreementState.tso,
                    oldAgreementState.portfolio,
                    oldAgreementState.sourceSystem,
                    oldAgreementState.programmingMethod,
                    oldAgreementState.agreementNote,
                    agreementUpdateProperty.energy,
                    agreementUpdateProperty.dcq,
                    agreementUpdateProperty.price,
                    agreementUpdateProperty.validFrom,
                    agreementUpdateProperty.validTo,
                    oldAgreementState.agreementDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(AgreementContract.Commands.Update(), newAgreementState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldAgreementStateRef)
                    .addOutputState(newAgreementState, AgreementContract.ID)
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

            val GSESession = initiateFlow(oldAgreementState.GSE)
            val counterpartSession = initiateFlow(oldAgreementState.counterpartParty)

            // Send the state to the counterparty, and receive it back with their signature.
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(GSESession, counterpartSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(GSESession, counterpartSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newAgreementState
        }
    }

    @InitiatedBy(UpdaterAgreement::class)
    class UpdateAcceptorAgreement(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an agreement transaction." using (output is AgreementState)
                    val agreement = output as AgreementState
                    /* "other rule agreement" using (agreement is new rule) */
                    "agreementID cannot be empty on update" using (agreement.agreementID.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

}
