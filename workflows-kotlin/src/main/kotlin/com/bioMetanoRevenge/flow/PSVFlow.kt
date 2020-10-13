package com.bioMetanoRevenge.flow

import co.paralleluniverse.fibers.Suspendable
import com.bioMetanoRevenge.contract.PSVContract
import com.bioMetanoRevenge.schema.PSVSchemaV1
import com.bioMetanoRevenge.state.PSVState
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
import pojo.PSVPojo
import pojo.PSVUpdateAuctionPojo
import pojo.PSVUpdateCheckPojo
import pojo.PSVUpdatePojo
import java.time.Instant
import java.util.*

object PSVFlow {

    /**
     *
     * Issue PSVState Flow ------------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class IssuerPSVState(val psvStateProperty: PSVPojo) : FlowLogic<PSVState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new PSVState.")
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
        override fun call(): PSVState {

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
                throw FlowException("Node $myLegalIdentity cannot start the issue-psvState flow. This flow can only be started from the Snam node")
            }

            // fixed Party value for GSE
            var GSEX500Name = CordaX500Name(organisation = "GSE", locality = "Milan", country = "IT")
            var GSEParty = serviceHub.networkMapCache.getPeerByLegalName(GSEX500Name)!!

            // set Party value for Seller
            var sellerX500Name = CordaX500Name(organisation = psvStateProperty.seller, locality = "Milan", country = "IT")
            var sellerParty = serviceHub.networkMapCache.getPeerByLegalName(sellerX500Name)!!

            // set Party value for Buyer
            var buyerX500Name = CordaX500Name(organisation = psvStateProperty.buyer, locality = "Milan", country = "IT")
            var buyerParty = serviceHub.networkMapCache.getPeerByLegalName(buyerX500Name)!!

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val psvState = PSVState(
                    GSEParty,
                    myLegalIdentity,
                    sellerParty,
                    buyerParty,
                    psvStateProperty.transactionType,
                    psvStateProperty.PIVASeller,
                    psvStateProperty.PIVABuyer,
                    psvStateProperty.parentBatchID,
                    psvStateProperty.transactionCode,
                    psvStateProperty.month,
                    psvStateProperty.remiCode,
                    psvStateProperty.plantAddress,
                    psvStateProperty.plantCode,
                    psvStateProperty.initialQuantity,
                    psvStateProperty.quantity,
                    psvStateProperty.price,
                    0.0,
                    psvStateProperty.pcs,
                    psvStateProperty.pci,
                    psvStateProperty.docRef,
                    psvStateProperty.docDate,
                    psvStateProperty.transactionStatus,
                    psvStateProperty.supportField,
                    psvStateProperty.auctionStatus,
                    "toCheck",
                    "toCheck",
                    Instant.now(),
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID()))

            val txCommand = Command(PSVContract.Commands.Issue(), psvState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(psvState, PSVContract.ID)
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
            val gseSession = initiateFlow(GSEParty)
            val sellerSession = initiateFlow(sellerParty)
            val buyerSession = initiateFlow(buyerParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(gseSession, sellerSession, buyerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(gseSession, sellerSession, buyerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return psvState
        }
    }

    @InitiatedBy(IssuerPSVState::class)
    class ReceiverPSVState(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an psvState transaction." using (output is PSVState)
                    val psvState = output as PSVState
                    /* "other rule psvState" using (psvState is new rule) */
                    "parentBatchID cannot be empty" using (psvState.parentBatchID.isNotEmpty())
                    "transactionCode cannot be empty" using (psvState.transactionCode.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update PSVState Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterPSVState(val psvStateUpdateProperty: PSVUpdatePojo) : FlowLogic<PSVState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update PSVState.")
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
        override fun call(): PSVState {

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
                throw FlowException("Node $myLegalIdentity cannot start the update-psvState flow. This flow can only be started from the Snam node")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for transactionCode
            var transactionCodeCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { PSVSchemaV1.PersistentPSV::transactionCode.equal(psvStateUpdateProperty.transactionCode) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

            val oldPSVStateStateList = serviceHub.vaultService.queryBy<PSVState>(
                    transactionCodeCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldPSVStateStateList.size > 1 || oldPSVStateStateList.isEmpty()) throw FlowException("No psvState state with transactionCode: ${psvStateUpdateProperty.transactionCode} found.")

            val oldPSVStateStateRef = oldPSVStateStateList[0]
            val oldPSVState = oldPSVStateStateRef.state.data

            // Generate an unsigned transaction.
            val newPSVState = PSVState(
                    oldPSVState.GSE,
                    myLegalIdentity,
                    oldPSVState.seller,
                    oldPSVState.buyer,
                    oldPSVState.transactionType,
                    oldPSVState.PIVASeller,
                    oldPSVState.PIVABuyer,
                    oldPSVState.parentBatchID,
                    oldPSVState.transactionCode,
                    oldPSVState.month,
                    oldPSVState.remiCode,
                    oldPSVState.plantAddress,
                    oldPSVState.plantCode,
                    oldPSVState.initialQuantity,
                    oldPSVState.quantity - psvStateUpdateProperty.sellingQuantity,
                    oldPSVState.price,
                    psvStateUpdateProperty.sellingPrice,
                    oldPSVState.pcs,
                    oldPSVState.pci,
                    oldPSVState.docRef,
                    oldPSVState.docDate,
                    psvStateUpdateProperty.transactionStatus,
                    oldPSVState.supportField,
                    oldPSVState.auctionStatus,
                    oldPSVState.snamCheck,
                    oldPSVState.financialCheck,
                    oldPSVState.transactionDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(PSVContract.Commands.Update(), newPSVState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldPSVStateStateRef)
                    .addOutputState(newPSVState, PSVContract.ID)
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
            val gseSession = initiateFlow(oldPSVState.GSE)
            val sellerSession = initiateFlow(oldPSVState.seller)
            val buyerSession = initiateFlow(oldPSVState.buyer)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(gseSession, sellerSession, buyerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(gseSession, sellerSession, buyerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newPSVState
        }
    }

    @InitiatedBy(UpdaterPSVState::class)
    class UpdateAcceptorPSVState(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an psvState transaction." using (output is PSVState)
                    val psvState = output as PSVState
                    /* "other rule psvState" using (output is new rule) */
                    "parentBatchID cannot be empty" using (psvState.parentBatchID.isNotEmpty())
                    "transactionCode cannot be empty" using (psvState.transactionCode.isNotEmpty())
                    "quantity must be greater or equal than zero" using (psvState.quantity >= 0.0)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update PSVState Auction Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterPSVStateAuction(val psvStateUpdateAuctionProperty: PSVUpdateAuctionPojo) : FlowLogic<PSVState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update PSVState Auction.")
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
        override fun call(): PSVState {

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
                throw FlowException("Node $myLegalIdentity cannot start the update-psvState-auction flow. This flow can only be started from the Snam node")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for transactionCode
            var transactionCodeCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { PSVSchemaV1.PersistentPSV::transactionCode.equal(psvStateUpdateAuctionProperty.transactionCode) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

            val oldPSVStateStateList = serviceHub.vaultService.queryBy<PSVState>(
                    transactionCodeCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldPSVStateStateList.size > 1 || oldPSVStateStateList.isEmpty()) throw FlowException("No psvState state with transactionCode: ${psvStateUpdateAuctionProperty.transactionCode} found.")

            val oldPSVStateStateRef = oldPSVStateStateList[0]
            val oldPSVState = oldPSVStateStateRef.state.data

            // Generate an unsigned transaction.
            val newPSVState = PSVState(
                    oldPSVState.GSE,
                    myLegalIdentity,
                    oldPSVState.seller,
                    oldPSVState.buyer,
                    oldPSVState.transactionType,
                    oldPSVState.PIVASeller,
                    oldPSVState.PIVABuyer,
                    oldPSVState.parentBatchID,
                    oldPSVState.transactionCode,
                    oldPSVState.month,
                    oldPSVState.remiCode,
                    oldPSVState.plantAddress,
                    oldPSVState.plantCode,
                    oldPSVState.initialQuantity,
                    oldPSVState.quantity,
                    oldPSVState.price,
                    oldPSVState.sellingPrice,
                    oldPSVState.pcs,
                    oldPSVState.pci,
                    oldPSVState.docRef,
                    oldPSVState.docDate,
                    oldPSVState.transactionStatus,
                    psvStateUpdateAuctionProperty.supportField,
                    psvStateUpdateAuctionProperty.auctionStatus,
                    oldPSVState.snamCheck,
                    oldPSVState.financialCheck,
                    oldPSVState.transactionDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(PSVContract.Commands.UpdateAuction(), newPSVState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldPSVStateStateRef)
                    .addOutputState(newPSVState, PSVContract.ID)
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
            val gseSession = initiateFlow(oldPSVState.GSE)
            val sellerSession = initiateFlow(oldPSVState.seller)
            val buyerSession = initiateFlow(oldPSVState.buyer)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(gseSession, sellerSession, buyerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(gseSession, sellerSession, buyerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newPSVState
        }
    }

    @InitiatedBy(UpdaterPSVStateAuction::class)
    class UpdateAuctionAcceptorPSVState(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an psvState transaction." using (output is PSVState)
                    val psvState = output as PSVState
                    /* "other rule psvState" using (output is new rule) */
                    "parentBatchID cannot be empty" using (psvState.parentBatchID.isNotEmpty())
                    "transactionCode cannot be empty" using (psvState.transactionCode.isNotEmpty())
                    "supportField cannot be empty" using (psvState.supportField.isNotEmpty())
                    "auctionStatus cannot be empty" using (psvState.auctionStatus.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    /***
     *
     * Update PSVState Check Flow -----------------------------------------------------------------------------------
     *
     * */
    @InitiatingFlow
    @StartableByRPC
    class UpdaterPSVStateCheck(val psvStateUpdateCheckProperty: PSVUpdateCheckPojo) : FlowLogic<PSVState>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on update PSVState Check.")
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
        override fun call(): PSVState {

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
                throw FlowException("Node $myLegalIdentity cannot start the update-psvState-check flow. This flow can only be started from the Snam node")
            }

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // setting the criteria for retrive UNCONSUMED state AND filter it for transactionCode
            var transactionCodeCriteria: QueryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression = builder { PSVSchemaV1.PersistentPSV::transactionCode.equal(psvStateUpdateCheckProperty.transactionCode) }, status = Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(PSVState::class.java))

            val oldPSVStateStateList = serviceHub.vaultService.queryBy<PSVState>(
                    transactionCodeCriteria,
                    PageSpecification(1, MAX_PAGE_SIZE),
                    Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))
            ).states

            if (oldPSVStateStateList.size > 1 || oldPSVStateStateList.isEmpty()) throw FlowException("No psvState state with transactionCode: ${psvStateUpdateCheckProperty.transactionCode} found.")

            val oldPSVStateStateRef = oldPSVStateStateList[0]
            val oldPSVState = oldPSVStateStateRef.state.data

            // Generate an unsigned transaction.
            val newPSVState = PSVState(
                    oldPSVState.GSE,
                    myLegalIdentity,
                    oldPSVState.seller,
                    oldPSVState.buyer,
                    oldPSVState.transactionType,
                    oldPSVState.PIVASeller,
                    oldPSVState.PIVABuyer,
                    oldPSVState.parentBatchID,
                    oldPSVState.transactionCode,
                    oldPSVState.month,
                    oldPSVState.remiCode,
                    oldPSVState.plantAddress,
                    oldPSVState.plantCode,
                    oldPSVState.initialQuantity,
                    oldPSVState.quantity,
                    oldPSVState.price,
                    oldPSVState.sellingPrice,
                    oldPSVState.pcs,
                    oldPSVState.pci,
                    oldPSVState.docRef,
                    oldPSVState.docDate,
                    oldPSVState.transactionStatus,
                    oldPSVState.supportField,
                    oldPSVState.auctionStatus,
                    psvStateUpdateCheckProperty.snamCheck,
                    psvStateUpdateCheckProperty.financialCheck,
                    oldPSVState.transactionDate,
                    Instant.now(),
                    UniqueIdentifier(id = UUID.randomUUID())
            )

            val txCommand = Command(PSVContract.Commands.UpdateCheck(), newPSVState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(oldPSVStateStateRef)
                    .addOutputState(newPSVState, PSVContract.ID)
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
            val gseSession = initiateFlow(oldPSVState.GSE)
            val sellerSession = initiateFlow(oldPSVState.seller)
            val buyerSession = initiateFlow(oldPSVState.buyer)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(gseSession, sellerSession, buyerSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(gseSession, sellerSession, buyerSession), FINALISING_TRANSACTION.childProgressTracker()))

            return newPSVState
        }
    }

    @InitiatedBy(UpdaterPSVStateCheck::class)
    class UpdateCheckAcceptorPSVState(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an psvState transaction." using (output is PSVState)
                    val psvState = output as PSVState
                    /* "other rule psvState" using (output is new rule) */
                    "parentBatchID cannot be empty" using (psvState.parentBatchID.isNotEmpty())
                    "transactionCode cannot be empty" using (psvState.transactionCode.isNotEmpty())
                    "snamCheck cannot be empty" using (psvState.snamCheck.isNotEmpty())
                    "financialCheck cannot be empty" using (psvState.financialCheck.isNotEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
