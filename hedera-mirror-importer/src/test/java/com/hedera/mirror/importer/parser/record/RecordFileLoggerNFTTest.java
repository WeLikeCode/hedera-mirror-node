package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import com.hedera.mirror.importer.parser.domain.RecordItem;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.util.Utility;

/**
 * Integration tests relating to RecordFileLogger and non_fee_transfers.
 */
public class RecordFileLoggerNFTTest extends AbstractRecordFileLoggerTest {

    private static final long PAYER_ACCOUNT_NUM = 1111;
    private static final AccountID PAYER_ACCOUNT_ID = AccountID.newBuilder().setAccountNum(PAYER_ACCOUNT_NUM).build();

    // New account/contract or the recipient of a transfer/call.
    private static final long NEW_ACCOUNT_NUM = 2222;
    private static final AccountID NEW_ACCOUNT_ID = AccountID.newBuilder().setAccountNum(NEW_ACCOUNT_NUM).build();
    private static final long NEW_CONTRACT_NUM = 2222;
    private static final ContractID NEW_CONTRACT_ID = ContractID.newBuilder().setContractNum(NEW_CONTRACT_NUM).build();

    private static final long NODE_ACCOUNT_NUM = 3;
    private static final AccountID NODE_ACCOUNT_ID = AccountID.newBuilder().setAccountNum(NODE_ACCOUNT_NUM).build();
    private static final long TREASURY_ACCOUNT_NUM = 98;
    private static final long PROXY_ACCOUNT_NUM = 999;
    private static final AccountID PROXY_ACCOUNT_ID = AccountID.newBuilder().setAccountNum(PROXY_ACCOUNT_NUM).build();

    private static final long NODE_FEE = 16;
    private static final long NETWORK_FEE = 32;
    private static final long SERVICE_FEE = 64;
    private static final long THRESHOLD_RECORD_FEE = 128;
    private static final long TRANSFER_AMOUNT = 256;
    private static final long NETWORK_SERVICE_FEE = NETWORK_FEE + SERVICE_FEE;
    private static final long CHARGED_FEE = NETWORK_SERVICE_FEE + NODE_FEE;
    private static final long MAX_FEE = 1_000_000;
    private static final long VALID_DURATION_SECONDS = 120;
    private static final long CONSENSUS_TIMESTAMP_SECONDS = 1_580_000_000L;

    private static final String MEMO = "crypto non fee transfer tests";

    private Set<Long> expectedEntityNum = new HashSet<>();
    private int expectedNonFeeTransfersCount;

    @Data
    @AllArgsConstructor
    class TransactionContext {
        private Transaction transaction;
        private TransactionRecord record;
    }

    private List<TransactionContext> expectedTransactions = new LinkedList<>();

    @BeforeEach
    void before() {
        parserProperties.setPersistCryptoTransferAmounts(true);
        parserProperties.setPersistNonFeeTransfers(false);

        expectedEntityNum.clear();
        expectedNonFeeTransfersCount = 0;
        expectedTransactions.clear();
    }

    @Test
    void contractCallItemizedTransfers() throws Exception {
        givenSuccessfulContractCallTransaction();
        assertEverything();
    }

    @Test
    void contractCallAggregatedTransfers() throws Exception {
        parserProperties.setPersistNonFeeTransfers(true);
        givenSuccessfulContractCallTransactionAggregatedTransfers();
        assertEverything();
    }

    @Test
    void contractCreateItemizedTransfers() throws Exception {
        givenSuccessfulContractCreateTransaction();
        assertEverything();
    }

    @Test
    void contractCreateAggregatedTransfers() throws Exception {
        parserProperties.setPersistNonFeeTransfers(true);
        givenSuccessfulContractCreateTransactionAggregatedTransfers();
        assertEverything();
    }

    @Test
    void cryptoCreateItemizedTransfers() throws Exception {
        givenSuccessfulCryptoCreateTransaction();
        assertEverything();
    }

    @Test
    void cryptoCreateItemizedTransfersStoreNonFeeTransfers() throws Exception {
        parserProperties.setPersistNonFeeTransfers(true);
        givenSuccessfulCryptoCreateTransaction();
        assertEverything();
    }

    @Test
    void cryptoCreateAggregatedTransfers() throws Exception {
        parserProperties.setPersistNonFeeTransfers(true);
        givenSuccessfulCryptoCreateTransactionAggregatedTransfers();
        assertEverything();
    }

    @Test
    void cryptoTransferItemizedTransfers() throws Exception {
        givenSuccessfulCryptoTransferTransaction();
        assertEverything();
    }

    @Test
    void cryptoTransferAggregatedTransfers() throws Exception {
        parserProperties.setPersistNonFeeTransfers(true);
        givenSuccessfulCryptoTransferTransactionAggregatedTransfers();
        assertEverything();
    }

    @Test
    void cryptoTransferFailedItemizedTransfers() throws Exception {
        givenFailedCryptoTransferTransaction();
        assertEverything();
    }

    @Test
    void cryptoTransferFailedAggregatedTransfers() throws Exception {
        givenFailedCryptoTransferTransactionAggregatedTransfers();
        assertEverything();
    }

    @Test
    void cryptoTransferFailedItemizedTransfersConfigAlways() throws Exception {
        parserProperties.setPersistNonFeeTransfers(true);
        givenFailedCryptoTransferTransaction();
        assertEverything();
    }

    private AccountAmount.Builder accountAmount(long accountNum, long amount) {
        return AccountAmount.newBuilder().setAccountID(AccountID.newBuilder().setAccountNum(accountNum))
                .setAmount(amount);
    }

    private void assertEntities() {
        var expected = expectedEntityNum.toArray();
        var actual = StreamSupport.stream(entityRepository.findAll().spliterator(), false)
                .map(e -> e.getEntityNum())
                .toArray();
        Arrays.sort(expected);
        Arrays.sort(actual);
        assertArrayEquals(expected, actual);
    }

    private void assertEverything() {
        assertAll(
                () -> assertRepositoryRowCounts()
                ,() -> assertTransactions()
                ,() -> assertEntities()
        );
    }

    private void assertRepositoryRowCounts() {
        var expectedTransfersCount = expectedTransactions.stream()
                .mapToInt(t -> t.record.getTransferList().getAccountAmountsList().size()).sum();
        assertAll(() -> assertEquals(expectedTransactions.size(), transactionRepository.count(), "t_transactions rows")
                , () -> assertEquals(expectedEntityNum.size(), entityRepository.count(), "t_entities rows")
                , () -> assertEquals(expectedTransfersCount, cryptoTransferRepository.count(), "t_cryptotransferlists rows")
                , () -> assertEquals(expectedNonFeeTransfersCount, nonFeeTransferRepository.count(), "non_fee_transfers rows")
        );
    }

    private void assertTransactions() {
        expectedTransactions.forEach(t -> {
            var dbTransaction = transactionRepository
                    .findById(Utility.timeStampInNanos(t.record.getConsensusTimestamp())).get();
            var dbNode = entityRepository.findByPrimaryKey(0, 0, NODE_ACCOUNT_NUM).get();
            var dbPayer = entityRepository.findByPrimaryKey(0, 0, PAYER_ACCOUNT_NUM).get();

            assertAll(
                    () -> assertEquals(dbNode.getId(), dbTransaction.getNodeAccountId())
                    ,() -> assertEquals(dbPayer.getId(), dbTransaction.getPayerAccountId())
            );
        });
    }

    private Transaction contractCall() {
        var inner = ContractCallTransactionBody.newBuilder()
                .setContractID(NEW_CONTRACT_ID)
                .setAmount(TRANSFER_AMOUNT);

        var body = transactionBody().setContractCall(inner);
        return Transaction.newBuilder()
                .setBodyBytes(body.build().toByteString())
                .setSigMap(getSigMap()).build();
    }

    private void contractCallWithTransferList(TransferList.Builder transferList) throws Exception {
        var transaction = contractCall();
        var transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        var record = transactionRecordSuccess(transactionBody, transferList).build();

        expectedTransactions.add(new TransactionContext(transaction, record));
        parseRecordItemAndCommit(new RecordItem(transaction, record));
    }

    private Transaction contractCreate() {
        var inner = ContractCreateTransactionBody.newBuilder()
                .setInitialBalance(TRANSFER_AMOUNT);

        var body = transactionBody().setContractCreateInstance(inner);
        return Transaction.newBuilder()
                .setBodyBytes(body.build().toByteString())
                .setSigMap(getSigMap()).build();
    }

    private void contractCreateWithTransferList(TransferList.Builder transferList) throws Exception {
        var transaction = contractCreate();
        var transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        var record = transactionRecordSuccess(transactionBody, transferList).build();

        expectedTransactions.add(new TransactionContext(transaction, record));
        parseRecordItemAndCommit(new RecordItem(transaction, record));
    }

    private Transaction cryptoCreate() {
        var inner = CryptoCreateTransactionBody.newBuilder()
            .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
            .setInitialBalance(TRANSFER_AMOUNT)
            .setKey(keyFromString("0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"))
            .setProxyAccountID(PROXY_ACCOUNT_ID);

        var body = transactionBody().setCryptoCreateAccount(inner);
        return Transaction.newBuilder()
            .setBodyBytes(body.build().toByteString())
            .setSigMap(getSigMap()).build();
    }

    private void cryptoCreateWithTransferList(TransferList.Builder transferList) throws Exception {
        var transaction = cryptoCreate();
        var transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        var record = transactionRecordSuccess(transactionBody, transferList).build();

        expectedTransactions.add(new TransactionContext(transaction, record));
        parseRecordItemAndCommit(new RecordItem(transaction, record));
    }

    private Transaction cryptoTransfer() {
        var nonFeeTransfers = TransferList.newBuilder()
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - TRANSFER_AMOUNT))
                .addAccountAmounts(accountAmount(NEW_ACCOUNT_NUM, 0 - TRANSFER_AMOUNT));
        var inner = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(nonFeeTransfers);

        var body = transactionBody().setCryptoTransfer(inner.build());
        return Transaction.newBuilder()
                .setBodyBytes(body.build().toByteString())
                .setSigMap(getSigMap()).build();
    }

    private void cryptoTransferWithTransferList(TransferList.Builder transferList, ResponseCodeEnum rc) throws Exception {
        var transaction = cryptoTransfer();
        var transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
        var record = transactionRecord(transactionBody, rc, transferList).build();

        expectedTransactions.add(new TransactionContext(transaction, record));
        parseRecordItemAndCommit(new RecordItem(transaction, record));
    }

    private void cryptoTransferWithTransferList(TransferList.Builder transferList) throws Exception {
        cryptoTransferWithTransferList(transferList, ResponseCodeEnum.SUCCESS);
    }

    private void givenSuccessfulContractCallTransaction() throws Exception {
        contractCallWithTransferList(transferListForContractCallItemized());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM, NEW_CONTRACT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenSuccessfulContractCallTransactionAggregatedTransfers() throws Exception {
        contractCallWithTransferList(transferListForContractCallAggregated());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM, NEW_CONTRACT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenSuccessfulContractCreateTransaction() throws Exception {
        contractCreateWithTransferList(transferListForContractCreateItemized());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM, NEW_CONTRACT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenSuccessfulContractCreateTransactionAggregatedTransfers() throws Exception {
        contractCreateWithTransferList(transferListForContractCreateAggregated());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM, NEW_CONTRACT_NUM));

        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenSuccessfulCryptoCreateTransaction() throws Exception {
        cryptoCreateWithTransferList(transferListForCryptoCreateItemized());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, PROXY_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM,
                NEW_ACCOUNT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenSuccessfulCryptoCreateTransactionAggregatedTransfers() throws Exception {
        cryptoCreateWithTransferList(transferListForCryptoCreateAggregated());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, PROXY_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM,
                NEW_ACCOUNT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenFailedCryptoTransferTransaction() throws Exception {
        cryptoTransferWithTransferList(transferListForFailedCryptoTransferItemized(),
                ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenFailedCryptoTransferTransactionAggregatedTransfers() throws Exception {
        cryptoTransferWithTransferList(transferListForFailedCryptoTransferAggregated(),
                ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM));
    }

    private void givenSuccessfulCryptoTransferTransaction() throws Exception {
        cryptoTransferWithTransferList(transferListForCryptoTransferItemized());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM, NEW_ACCOUNT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private void givenSuccessfulCryptoTransferTransactionAggregatedTransfers() throws Exception {
        cryptoTransferWithTransferList(transferListForCryptoTransferAggregated());
        expectedEntityNum.addAll(List.of(PAYER_ACCOUNT_NUM, NODE_ACCOUNT_NUM, TREASURY_ACCOUNT_NUM, NEW_ACCOUNT_NUM));
        if (parserProperties.isPersistNonFeeTransfers()) {
            expectedNonFeeTransfersCount += 2;
        }
    }

    private TransactionBody.Builder transactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionFee(MAX_FEE)
                .setMemo(MEMO)
                .setNodeAccountID(NODE_ACCOUNT_ID)
                .setTransactionID(Utility.getTransactionId(PAYER_ACCOUNT_ID))
                .setTransactionValidDuration(Duration.newBuilder().setSeconds(VALID_DURATION_SECONDS).build());
    }

    private TransactionRecord.Builder transactionRecord(TransactionBody transactionBody, ResponseCodeEnum responseCode,
                                                        TransferList.Builder transferList) {
        return transactionRecord(transactionBody, responseCode.getNumber(), transferList);
    }

    private TransactionRecord.Builder transactionRecord(TransactionBody transactionBody, int responseCode,
                                                        TransferList.Builder transferList) {
        var receipt = TransactionReceipt.newBuilder().setStatusValue(responseCode);
        if (responseCode == ResponseCodeEnum.SUCCESS.getNumber()) {
            if (transactionBody.hasCryptoCreateAccount()) {
                receipt.setAccountID(NEW_ACCOUNT_ID);
            } else if (transactionBody.hasContractCreateInstance()) {
                receipt.setContractID(NEW_CONTRACT_ID);
            }
        }

        return TransactionRecord.newBuilder()
                .setReceipt(receipt)
                .setConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(CONSENSUS_TIMESTAMP_SECONDS + expectedTransactions.size()))
                .setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()))
                .setTransactionHash(ByteString.copyFromUtf8("hash"))
                .setTransactionID(transactionBody.getTransactionID())
                .setTransactionFee((responseCode == ResponseCodeEnum.SUCCESS.getNumber()) ? CHARGED_FEE : NODE_FEE)
                .setTransferList(transferList);
    }

    private TransactionRecord.Builder transactionRecordSuccess(TransactionBody transactionBody,
                                                               TransferList.Builder transferList) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS, transferList);
    }

    private TransferList.Builder transferListForContractCallItemized() {
        // They happen to be the same here (initialBalance same as transfer amount).
        return transferListForContractCreateItemized();
    }

    private TransferList.Builder transferListForContractCallAggregated() {
        // They happen to be the same here (initialBalance same as transfer amount).
        return transferListForContractCreateAggregated();
    }

    private TransferList.Builder transferListForContractCreateItemized() {
        return TransferList.newBuilder()
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - TRANSFER_AMOUNT))
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - CHARGED_FEE))
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - THRESHOLD_RECORD_FEE))
                .addAccountAmounts(accountAmount(NEW_CONTRACT_NUM, TRANSFER_AMOUNT))
                .addAccountAmounts(accountAmount(NODE_ACCOUNT_NUM, NODE_FEE))
                .addAccountAmounts(accountAmount(TREASURY_ACCOUNT_NUM, NETWORK_FEE))
                .addAccountAmounts(accountAmount(TREASURY_ACCOUNT_NUM, THRESHOLD_RECORD_FEE))
                .addAccountAmounts(accountAmount(TREASURY_ACCOUNT_NUM, SERVICE_FEE));
    }

    private TransferList.Builder transferListForContractCreateAggregated() {
        return TransferList.newBuilder()
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - TRANSFER_AMOUNT - CHARGED_FEE))
                .addAccountAmounts(accountAmount(NEW_CONTRACT_NUM, TRANSFER_AMOUNT))
                .addAccountAmounts(accountAmount(NODE_ACCOUNT_NUM, NODE_FEE))
                .addAccountAmounts(accountAmount(TREASURY_ACCOUNT_NUM, NETWORK_SERVICE_FEE));
    }

    private TransferList.Builder transferListForCryptoCreateItemized() {
        return TransferList.newBuilder()
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - TRANSFER_AMOUNT))
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - NODE_FEE))
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - NETWORK_SERVICE_FEE))
                .addAccountAmounts(accountAmount(NEW_ACCOUNT_NUM, TRANSFER_AMOUNT))
                .addAccountAmounts(accountAmount(NODE_ACCOUNT_NUM, NODE_FEE))
                .addAccountAmounts(accountAmount(TREASURY_ACCOUNT_NUM, NETWORK_FEE))
                .addAccountAmounts(accountAmount(TREASURY_ACCOUNT_NUM, SERVICE_FEE));
    }

    private TransferList.Builder transferListForCryptoCreateAggregated() {
        return TransferList.newBuilder()
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - TRANSFER_AMOUNT - CHARGED_FEE))
                .addAccountAmounts(accountAmount(NEW_ACCOUNT_NUM, TRANSFER_AMOUNT))
                .addAccountAmounts(accountAmount(NODE_ACCOUNT_NUM, NODE_FEE))
                .addAccountAmounts(accountAmount(TREASURY_ACCOUNT_NUM, NETWORK_SERVICE_FEE));
    }

    private TransferList.Builder transferListForCryptoTransferItemized() {
        // They happen to be the same here (initialBalance same as transfer amount).
        return transferListForCryptoCreateItemized();
    }

    private TransferList.Builder transferListForCryptoTransferAggregated() {
        // They happen to be the same here (initialBalance same as transfer amount).
        return transferListForCryptoCreateAggregated();
    }

    private TransferList.Builder transferListForFailedCryptoTransferItemized() {
        return TransferList.newBuilder()
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - NODE_FEE))
                .addAccountAmounts(accountAmount(NODE_ACCOUNT_NUM, NODE_FEE));
    }

    private TransferList.Builder transferListForFailedCryptoTransferAggregated() {
        return TransferList.newBuilder()
                .addAccountAmounts(accountAmount(PAYER_ACCOUNT_NUM, 0 - NODE_FEE))
                .addAccountAmounts(accountAmount(NODE_ACCOUNT_NUM, NODE_FEE));
    }
}
