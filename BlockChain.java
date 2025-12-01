import java.util.ArrayList;
import java.util.HashMap;

import java.util.Base64;

/* Block Chain should maintain only limited block nodes to satisfy the functions
   You should not have the all the blocks added to the block chain in memory 
   as it would overflow memory
 */

public class BlockChain {
   public static final int CUT_OFF_AGE = 10;

   // all information required in handling a block in block chain
   private class BlockNode {
      public Block b;
      public BlockNode parent;
      public ArrayList<BlockNode> children;
      public int height;
      // utxo pool for making a new block on top of this block
      private UTXOPool uPool;

      public BlockNode(Block b, BlockNode parent, UTXOPool uPool) {
         this.b = b;
         this.parent = parent;
         children = new ArrayList<BlockNode>();
         this.uPool = uPool;
         if (parent != null) {
            height = parent.height + 1;
            parent.children.add(this);
         } else {
            height = 1;
         }
      }

      public UTXOPool getUTXOPoolCopy() {
         return new UTXOPool(uPool);
      }
   }

   /* create an empty block chain with just a genesis block.
    * Assume genesis block is a valid block
    */
   private HashMap<String, BlockNode> blockNodeHashmap;
   private TransactionPool transactionPool;
   private BlockNode maxHeightBlock;

   public BlockChain(Block genesisBlock) {
      // IMPLEMENT THIS

      //Initializing Hashmap
      this.blockNodeHashmap = new HashMap<>();
      //Initializing transactionPool
      this.transactionPool = new TransactionPool();


      //Creating UTXO pool
      UTXOPool utxoPool = new UTXOPool();
      //adding OG block coinbase to UTXOPool
      Transaction initCoinbase = genesisBlock.getCoinbase();
      byte[] coinbaseHash = initCoinbase.getHash();
      int outputNum = initCoinbase.numOutputs();

      for (int i = 0; i < outputNum; i++) {
         Transaction.Output tempOutput = initCoinbase.getOutput(i);
         UTXO tempUTXO = new UTXO(coinbaseHash, i);
         utxoPool.addUTXO(tempUTXO, tempOutput);
      }

      //Make blocknode
      BlockNode blockNode = new BlockNode(genesisBlock, null, utxoPool);

      //Block hashmap
      //Getting init block hash
      genesisBlock.finalize();
      byte[] initBlockHash = genesisBlock.getHash();
      String blockHash = Base64.getEncoder().encodeToString(initBlockHash);

      blockNodeHashmap.put(blockHash, blockNode);

      //Init maxHeightBlock
      this.maxHeightBlock = blockNode;

   }

   /* Get the maximum height block
    */
   public Block getMaxHeightBlock() {
      // IMPLEMENT THIS
      return maxHeightBlock.b;
   }
   
   /* Get the UTXOPool for mining a new block on top of 
    * max height block
    */
   public UTXOPool getMaxHeightUTXOPool() {
      // IMPLEMENT THIS
      return maxHeightBlock.getUTXOPoolCopy();
   }
   
   /* Get the transaction pool to mine a new block
    */
   public TransactionPool getTransactionPool() {
      // IMPLEMENT THIS
      return transactionPool;
   }

   /* Add a block to block chain if it is valid.
    * For validity, all transactions should be valid
    * and block should be at height > (maxHeight - CUT_OFF_AGE).
    * For example, you can try creating a new block over genesis block 
    * (block height 2) if blockChain height is <= CUT_OFF_AGE + 1. 
    * As soon as height > CUT_OFF_AGE + 1, you cannot create a new block at height 2.
    * Return true of block is successfully added
    */
   public boolean addBlock(Block b) {
       // IMPLEMENT THIS
       //validity checks
       byte[] bPrevBlockHash = b.getPrevBlockHash();
       if (bPrevBlockHash == null) {
         return false;
       }
       String newBPrevBlockHash = Base64.getEncoder().encodeToString(bPrevBlockHash);
       if (blockNodeHashmap.containsKey(newBPrevBlockHash) == false) {
         return false;
       }
       BlockNode prevBlock = blockNodeHashmap.get(newBPrevBlockHash);

       int blockHeight = prevBlock.height + 1;

       if (blockHeight <= (maxHeightBlock.height - CUT_OFF_AGE)) {
         return false;
       }

       UTXOPool prevBlockUTXOPool = prevBlock.getUTXOPoolCopy();
       TxHandler txHandler = new TxHandler(prevBlockUTXOPool);
       ArrayList<Transaction> blockTransactions = b.getTransactions();
       Transaction[] blockTransactionsArray = blockTransactions.toArray(new Transaction[0]);
       Transaction[] validatedTransactions = txHandler.handleTxs(blockTransactionsArray);
       if(blockTransactionsArray.length != validatedTransactions.length) {
         return false;
       }

       //Updating UTXOPool
       UTXOPool updatedUTXOPool = txHandler.getUTXOPool();
       Transaction newCoinbase = b.getCoinbase();

       byte[] newCoinbaseHash = newCoinbase.getHash();
       int coinbaseOutputNum = newCoinbase.numOutputs();

      for (int i = 0; i < coinbaseOutputNum; i++) {
         Transaction.Output tempOutput = newCoinbase.getOutput(i);
         UTXO tempUTXO = new UTXO(newCoinbaseHash, i);
         updatedUTXOPool.addUTXO(tempUTXO, tempOutput);

      }

      //Getting block hash
      b.finalize();
      byte[] ogBlockHash = b.getHash();

      //Make blocknode
      BlockNode bBlockNode = new BlockNode(b, prevBlock, updatedUTXOPool);

      //Block hashmap
      String bBlockHash = Base64.getEncoder().encodeToString(ogBlockHash);

      blockNodeHashmap.put(bBlockHash, bBlockNode);

      //setting maxHeightBlock
      if (bBlockNode.height > maxHeightBlock.height) {
         maxHeightBlock = bBlockNode;
      }

      //Update TransactionPool
      for (int i = 0; i < validatedTransactions.length; i++) {
         byte[] vTxHash = validatedTransactions[i].getHash();
         this.transactionPool.removeTransaction(vTxHash);
      }
      
      return true;
   }

   /* Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      // IMPLEMENT THIS
      transactionPool.addTransaction(tx);
      
   }
}
