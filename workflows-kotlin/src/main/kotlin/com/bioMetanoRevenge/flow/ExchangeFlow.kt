package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.ExchangeContract
import com.bioMetanoRevenge.schema.ExchangeSchemaV1
import com.bioMetanoRevenge.state.ExchangeState
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
import pojo.ExchangePojo
import pojo.ExchangeUpdatePojo
import java.time.Instant
import java.util.*

object ExchangeFlow {

    /**
     *
     * Issue Exchange Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerExchange(val exchangeProperty: ExchangePojo) : FlowLogic<ExchangeState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Exchange.")
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
        override fun call(): ExchangeState {

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
                throw FlowException("Node $myLegalIdentity cannot start the issue-exchange flow. This flow can only be started from the GSE node")
            }

            // set Party value for Seller
            var sellerX500Name = CordaX500Name(organisation = exchangeProperty.seller, locality = "Milan", country = "IT")
            var sellerParty = serviceHub.networkMapCache.getPeerByLegalName(sellerX500Name)!!

            // set Party value for Buyer
            var buyerX500Name = CordaX500Name(organisation = exchangeProperty.buyer, locality = "Milan", country = "IT")
            var buyerParty = serviceHub.networkMapCache.getPeerByLegalName(buyerX500Name)!!

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val exchangeState = ExchangeState(
                    myLegalIdentity,
                    sellerParty,
                    buyerParty,
                    exchangeProperty.exchangeType,
                    exchangeProperty.PIVASeller,
                    exchangeProperty.PIVABuyer,
                    exchangeProperty.parentBatchID,
                    exchangeProperty.exchangeCode,
                    exchangeProperty.month,
                    exchangeProperty.remiCode,
                    exchangeProperty.plantAddress,
                    exchangeProperty.plantCode,
                    exchangeProperty.hauler,
                    exchangeProperty.PIVAHauler,
                    exchangeProperty.trackCode,
                    exchangeProperty.pickupDate,
                    exchangeProperty.deliveryDate,
                    exchangeProperty.quantity,
                    exchangeProperty.price,
                    exchangeProperty.startingPosition,
                    exchangeProperty.arrivalPosition,
                    exchangeProperty.docRef,
                    exchangeProperty.docDate,
                    exchangeProperty.exchangeStatus,
                    Instant.now(),
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(ExchangeContract.Commands.Issue(), exchangeState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(exchangeState, ExchangeContract.ID)
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
            val sellerSession = initiateFlow(sellerParty)
            val buyerSession = initiateFlow(buyerParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(sellerSession, buyerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(sellerSession, buyerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return exchangeState
        }
    }

    @InitiatedBy(IssuerExchange::class)
    class ReceiverExchange(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an exchange transaction." using (output is ExchangeState)
                    val exchangeState = output as ExchangeState
                    /* "other rule exchange" using (exchange is new rule) */
                    "parentBatchID cannot be empty" using (exchangeState.parentBatchID.isNotEmpty())
                    "exchangeCode cannot be empty" using (exchangeState.exchangeCode.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update Exchange Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterExchange(val exchangeUpdateProperty: ExchangeUpdatePojo) : FlowLogic<ExchangeState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update Exchange.")
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
        override fun call(): ExchangeState {

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
                throw FlowException("Node $myLegalIdentity cannot start the update-exchange flow. This flow can only be started from the GSE node")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for exchangeCode
            var exchangeCodeCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { ExchangeSchemaV1.PersistentExchange::exchangeCode.equal(exchangeUpdateProperty.exchangeCode) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(ExchangeState::class.java))

            val oldExchangeStateList = serviceHub.vaultService.queryBy<ExchangeState>(
                    exchangeCodeCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldExchangeStateList.size > 1 || oldExchangeStateList.isEmpty()) throw FlowException("No exchange state with exchangeCode: ${exchangeUpdateProperty.exchangeCode} found.")

            val oldExchangeStateRef = oldExchangeStateList[0]
            val oldExchangeState = oldExchangeStateRef.state.data

            // Generate an unsigned transaction.
            val newExchangeState = ExchangeState(
                    myLegalIdentity,
                    oldExchangeState.seller,
                    oldExchangeState.buyer,
                    oldExchangeState.exchangeType,
                    oldExchangeState.PIVASeller,
                    oldExchangeState.PIVABuyer,
                    oldExchangeState.parentBatchID,
                    oldExchangeState.exchangeCode,
                    oldExchangeState.month,
                    oldExchangeState.remiCode,
                    oldExchangeState.plantAddress,
                    oldExchangeState.plantCode,
                    oldExchangeState.hauler,
                    oldExchangeState.PIVAHauler,
                    oldExchangeState.trackCode,
                    oldExchangeState.pickupDate,
                    oldExchangeState.deliveryDate,
                    oldExchangeState.quantity,
                    oldExchangeState.price,
                    oldExchangeState.startingPosition,
                    oldExchangeState.arrivalPosition,
                    oldExchangeState.docRef,
                    oldExchangeState.docDate,
                    exchangeUpdateProperty.exchangeStatus,
                    oldExchangeState.exchangeDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(ExchangeContract.Commands.Update(), newExchangeState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldExchangeStateRef)
                    .addOutputState(newExchangeState, ExchangeContract.ID)
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
            val sellerSession = initiateFlow(oldExchangeState.seller)
            val buyerSession = initiateFlow(oldExchangeState.buyer)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(sellerSession, buyerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(sellerSession, buyerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newExchangeState
        }
    }

    @InitiatedBy(UpdaterExchange::class)
    class UpdateAcceptorExchange(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an exchange transaction." using (output is ExchangeState)
                    val exchangeState = output as ExchangeState
                    /* "other rule exchange" using (output is new rule) */
                    "parentBatchID cannot be empty" using (exchangeState.parentBatchID.isNotEmpty())
                    "exchangeCode cannot be empty" using (exchangeState.exchangeCode.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
