/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.RepositoryTrack;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 08/08/2016.
 */
public class TransactionPoolImplTest {

    private static final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void usingRepository() {
        TransactionPoolImpl pendingState = createSampleNewPendingState(createBlockchain());

        Repository repository = pendingState.getRepository();

        Assert.assertNotNull(repository);
        Assert.assertTrue(repository instanceof RepositoryTrack);
    }

    @Test
    public void usingInit() {
        TransactionPoolImpl pendingState = createSampleNewPendingState(createBlockchain());

        Assert.assertFalse(pendingState.hasCleanerFuture());
        Assert.assertNotEquals(0, pendingState.getOutdatedThreshold());
        Assert.assertNotEquals(0, pendingState.getOutdatedTimeout());
    }

    @Test
    public void usingCleanUp() {
        TransactionPoolImpl pendingState = createSampleNewPendingState(createBlockchain());

        pendingState.cleanUp();

        Assert.assertTrue(pendingState.getPendingTransactions().isEmpty());
    }

    @Test
    public void usingStart() {
        BlockChainImpl blockchain = createBlockchain();
        TransactionPoolImpl pendingState = createSampleNewPendingState(blockchain);

        pendingState.start(blockchain.getBestBlock());

        Assert.assertTrue(pendingState.hasCleanerFuture());

        pendingState.stop();

        Assert.assertFalse(pendingState.hasCleanerFuture());
    }

    @Test
    public void usingAccountsWithInitialBalance() {
        TransactionPoolImpl pendingState = createSampleNewPendingStateWithAccounts(2, Coin.valueOf(10L), createBlockchain());

        Repository repository = pendingState.getRepository();

        Assert.assertNotNull(repository);

        Account account1 = createAccount(1);
        Account account2 = createAccount(2);

        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account1.getAddress()).asBigInteger());
        Assert.assertEquals(BigInteger.TEN, repository.getBalance(account2.getAddress()).asBigInteger());
    }

    @Test
    public void getEmptyPendingTransactionList() {
        TransactionPoolImpl pendingState = createSampleNewPendingState(createBlockchain());

        List<Transaction> transactions = pendingState.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void addAndGetPendingTransaction() {
        TransactionPoolImpl transactionPool = createSampleNewPendingState(createBlockchain());
        Transaction tx = createSampleTransaction();

        transactionPool.addTransaction(tx);
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addAndExecutePendingTransaction() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx);

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1001000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void addAndExecuteTwoPendingTransaction() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl pendingState = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        pendingState.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Account receiver = createAccount(2);

        pendingState.addTransaction(tx1);
        pendingState.addTransaction(tx2);

        Repository repository = pendingState.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1004000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void rejectPendingStateTransaction() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx = createSampleTransaction(1, 2, 1000, 0);
        tx.setGasLimit(BigInteger.valueOf(3000001).toByteArray());
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx);

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1000000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void removeObsoletePendingTransactionsByBlock() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assert.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(1, 1, 100);

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(20, transactionPool.getOutdatedThreshold(), 100);

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void removeObsoletePendingTransactionsByTimeout() throws InterruptedException {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        Assert.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        Thread.sleep(3000);

        transactionPool.removeObsoleteTransactions(1, 1, 1);

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void removeObsoleteWireTransactions() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);

        transactionPool.addTransactions(txs);

        Assert.assertEquals(10, transactionPool.getOutdatedThreshold());

        List<Transaction> list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(1, 1, 0);

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertFalse(list.isEmpty());
        Assert.assertEquals(2, list.size());

        transactionPool.removeObsoleteTransactions(20, transactionPool.getOutdatedThreshold(), transactionPool.getOutdatedTimeout());

        list = transactionPool.getPendingTransactions();

        Assert.assertNotNull(list);
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void getAllPendingTransactions() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl pendingState = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        pendingState.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);

        pendingState.addTransaction(tx1);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx2);
        pendingState.addTransactions(txs);

        List<Transaction> alltxs = pendingState.getPendingTransactions();

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(2, alltxs.size());
        Assert.assertTrue(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
    }

    @Test
    public void processBestBlockRemovesTransactionsInBlock() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl pendingState = createSampleNewPendingStateWithAccounts(3, balance, blockchain);
        pendingState.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Transaction tx3 = createSampleTransaction(2, 3, 1000, 0);
        Transaction tx4 = createSampleTransaction(2, 3, 3000, 1);

        pendingState.addTransaction(tx1);
        pendingState.addTransaction(tx2);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx3);
        txs.add(tx4);
        pendingState.addTransactions(txs);

        List<Transaction> btxs = new ArrayList<>();
        btxs.add(tx1);
        btxs.add(tx3);

        Block genesis = blockchain.getBestBlock();
        Block block = new BlockBuilder().parent(genesis).transactions(btxs).build();

        pendingState.getBlockStore().saveBlock(genesis, new BlockDifficulty(BigInteger.ONE), true);
        pendingState.processBest(block);

        List<Transaction> alltxs = pendingState.getPendingTransactions();

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(2, alltxs.size());
        Assert.assertFalse(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
        Assert.assertFalse(alltxs.contains(tx3));
        Assert.assertTrue(alltxs.contains(tx4));

        Assert.assertSame(block, pendingState.getBestBlock());
    }

    @Test
    public void retractBlockAddsTransactionsAsWired() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl pendingState = createSampleNewPendingStateWithAccounts(3, balance, blockchain);
        pendingState.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Transaction tx3 = createSampleTransaction(2, 3, 1000, 0);
        Transaction tx4 = createSampleTransaction(2, 3, 3000, 1);

        pendingState.addTransaction(tx1);
        pendingState.addTransaction(tx2);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx3);
        txs.add(tx4);

        Block block = new BlockBuilder().parent(new BlockGenerator().getGenesisBlock()).transactions(txs).build();

        pendingState.retractBlock(block);

        List<Transaction> alltxs = pendingState.getPendingTransactions();

        Assert.assertNotNull(alltxs);
        Assert.assertFalse(alltxs.isEmpty());
        Assert.assertEquals(4, alltxs.size());
        Assert.assertTrue(alltxs.contains(tx1));
        Assert.assertTrue(alltxs.contains(tx2));
        Assert.assertTrue(alltxs.contains(tx3));
        Assert.assertTrue(alltxs.contains(tx4));

        List<Transaction> wtxs = pendingState.getPendingTransactions();

        Assert.assertNotNull(wtxs);
        Assert.assertFalse(wtxs.isEmpty());
        Assert.assertEquals(2, wtxs.size());
        Assert.assertTrue(wtxs.contains(tx3));
        Assert.assertTrue(wtxs.contains(tx4));
    }

    @Test
    public void updatePendingState() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl transactionPool = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        transactionPool.processBest(blockchain.getBestBlock());
        Transaction tx1 = createSampleTransaction(1, 2, 1000, 0);
        Transaction tx2 = createSampleTransaction(1, 2, 3000, 1);
        Account receiver = createAccount(2);

        transactionPool.addTransaction(tx1);
        transactionPool.addTransaction(tx2);

        transactionPool.updateState();

        Repository repository = transactionPool.getRepository();
        Assert.assertEquals(BigInteger.valueOf(1004000), repository.getBalance(receiver.getAddress()).asBigInteger());
    }

    @Test
    public void addTwiceAndGetPendingTransaction() {
        TransactionPoolImpl transactionPool = createSampleNewPendingState(createBlockchain());
        Transaction tx = createSampleTransaction();

        transactionPool.addTransaction(tx);
        transactionPool.addTransaction(tx);
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertEquals(1, transactions.size());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void getEmptyWireTransactionList() {
        TransactionPoolImpl transactionPool = createSampleNewPendingState(createBlockchain());

        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    @Test
    public void addAndGetWireTransaction() {
        TransactionPoolImpl pendingState = createSampleNewPendingState(createBlockchain());
        Transaction tx = createSampleTransaction();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        pendingState.addTransactions(txs);
        List<Transaction> transactions = pendingState.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addTwiceAndGetWireTransaction() {
        TransactionPoolImpl transactionPool = createSampleNewPendingState(createBlockchain());
        Transaction tx = createSampleTransaction();

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        txs.add(tx);

        transactionPool.addTransactions(txs);

        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertFalse(transactions.isEmpty());
        Assert.assertEquals(1, transactions.size());
        Assert.assertTrue(transactions.contains(tx));
    }

    @Test
    public void addWireTransactionThatAlreadyExistsAsPendingTransaction() {
        TransactionPoolImpl transactionPool = createSampleNewPendingState(createBlockchain());
        Transaction tx = createSampleTransaction();

        transactionPool.addTransaction(tx);
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);
        transactionPool.addTransactions(txs);
        List<Transaction> transactions = transactionPool.getPendingTransactions();

        Assert.assertNotNull(transactions);
        Assert.assertTrue(transactions.isEmpty());
    }

    
    @Test
    public void executeContractWithFakeBlock() {
        BlockChainImpl blockchain = createBlockchain();
        Coin balance = Coin.valueOf(1000000);
        TransactionPoolImpl pendingState = createSampleNewPendingStateWithAccounts(2, balance, blockchain);
        pendingState.processBest(blockchain.getBestBlock());
        // "NUMBER PUSH1 0x00 SSTORE" compiled to bytecodes
        String code = "43600055";
        Transaction tx = createSampleTransactionWithData(1, 0, code);

        pendingState.addTransaction(tx);

        Assert.assertNotNull(tx.getContractAddress().getBytes());
        // Stored value at 0 position should be 1, one more than the blockchain best block
        Assert.assertEquals(DataWord.ONE, pendingState.getRepository().getStorageValue(tx.getContractAddress(), DataWord.ZERO));
    }

    private static TransactionPoolImpl createSampleNewPendingState(BlockChainImpl blockChain) {
        TransactionPoolImpl transactionPool = new TransactionPoolImpl(config, blockChain.getRepository(), blockChain.getBlockStore(), null, new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);
        transactionPool.processBest(blockChain.getBestBlock());
        return transactionPool;
    }

    private static TransactionPoolImpl createSampleNewPendingStateWithAccounts(int naccounts, Coin balance, BlockChainImpl blockChain) {

        Block best = blockChain.getStatus().getBestBlock();
        Repository repository = blockChain.getRepository();

        Repository track = repository.startTracking();

        for (int k = 1; k <= naccounts; k++) {
            Account account = createAccount(k);
            track.createAccount(account.getAddress());
            track.addBalance(account.getAddress(), balance);
        }

        track.commit();

        best.setStateRoot(repository.getRoot());
        best.flushRLP();

        TransactionPoolImpl pendingState = new TransactionPoolImpl(config, blockChain.getRepository(), blockChain.getBlockStore(), null, new ProgramInvokeFactoryImpl(), new BlockExecutorTest.SimpleEthereumListener(), 10, 100);
        blockChain.setTransactionPool(pendingState);

        return pendingState;
    }

    private static Account createAccount(int naccount) {
        return new AccountBuilder().name("account" + naccount).build();
    }

    private static Transaction createSampleTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.TEN)
                .build();

        return tx;
    }

    private static Transaction createSampleTransaction(int from, int to, long value, int nonce) {
        Account sender = createAccount(from);
        Account receiver = createAccount(to);

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .nonce(nonce)
                .value(BigInteger.valueOf(value))
                .build();

        return tx;
    }

    private static Transaction createSampleTransactionWithData(int from, int nonce, String data) {
        Account sender = createAccount(from);

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiverAddress(new byte[0])
                .nonce(nonce)
                .data(data)
                .gasLimit(BigInteger.valueOf(1000000))
                .build();

        return tx;
    }

    private static BlockChainImpl createBlockchain() {
        BlockChainImpl blockChain = new BlockChainBuilder().build();
        Block genesis = BlockChainImplTest.getGenesisBlock(blockChain);
        blockChain.setStatus(genesis, genesis.getCumulativeDifficulty());
        return blockChain;
    }
}
