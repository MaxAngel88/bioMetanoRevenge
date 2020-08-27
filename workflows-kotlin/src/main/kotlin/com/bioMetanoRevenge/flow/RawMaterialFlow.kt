package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.RawMaterialContract
import com.bioMetanoRevenge.state.RawMaterialState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import pojo.RawMaterialPojo
import java.time.Instant
import java.util.*

object RawMaterialFlow {

    /**
     *
     * Issue Raw Material Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerRawMaterial(val rawMaterialProperty: RawMaterialPojo) : FlowLogic<RawMaterialState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new RawMaterial.")
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
        override fun call(): RawMaterialState {

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

            val produttore : Party = serviceHub.myInfo.legalIdentities.first()

            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // Generate an unsigned transaction.
            val rawMaterialState = RawMaterialState(
                    GSEParty,
                    produttore,
                    rawMaterialProperty.rawMaterialChar,
                    rawMaterialProperty.rawMaterialType,
                    rawMaterialProperty.CERCode,
                    rawMaterialProperty.quantity,
                    rawMaterialProperty.originCountry,
                    rawMaterialProperty.secondaryHarvest,
                    rawMaterialProperty.degradedLand,
                    rawMaterialProperty.sustainabilityCode,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(RawMaterialContract.Commands.Issue(), rawMaterialState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(rawMaterialState, RawMaterialContract.ID)
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

            return rawMaterialState
        }
    }

    @InitiatedBy(IssuerRawMaterial::class)
    class ReceiverRawMaterial(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an raw material transaction." using (output is RawMaterialState)
                    val rawMaterial = output as RawMaterialState
                    /* "other rule rawMaterial" using (rawMaterial is new rule) */
                    "CERCode cannot be empty" using (rawMaterial.CERCode.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}