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
      //Initializing Hashmap
      this.blockNodeHashmap = new HashMap<>();
      //Initializing transactionPool
      this.transactionPool = new TransactionPool();

      //Create empty UTXOpool
      UTXOPool utxoPool = new UTXOPool();
      BlockNode blockNode = addBlockNode(genesisBlock, null, utxoPool);

      //Initializimg maxHeightBlock
      this.maxHeightBlock = blockNode;
   }

   /* Get the maximum height block
    */
   public Block getMaxHeightBlock() {
      return maxHeightBlock.b;
   }
   
   /* Get the UTXOPool for mining a new block on top of 
    * max height block
    */
   public UTXOPool getMaxHeightUTXOPool() {
      return maxHeightBlock.getUTXOPoolCopy();
   }
   
   /* Get the transaction pool to mine a new block
    */
   public TransactionPool getTransactionPool() {
      return transactionPool;
   }

   public BlockNode addBlockNode(Block block, BlockNode prevBlock, UTXOPool utxoPool) {
      //Get coinbase
      Transaction coinbase = block.getCoinbase();
      //Get coinbase hash
      byte[] coinbaseHash = coinbase.getHash();
      //Get number of coinbase outputs
      int coinbaseOutputNum = coinbase.numOutputs();

      //Adding to UXTOPool
      for (int i = 0; i < coinbaseOutputNum; i++) {
            Transaction.Output tempOutput = coinbase.getOutput(i);
            UTXO tempUTXO = new UTXO(coinbaseHash, i);
            utxoPool.addUTXO(tempUTXO, tempOutput);

         }

      //Getting block hash
      block.finalize();
      byte[] blockHash = block.getHash();
      String blockHashKey = Base64.getEncoder().encodeToString(blockHash);
      //Creating blocknode
      BlockNode blockNode = new BlockNode(block, prevBlock, utxoPool);
      //Updating global hashmap
      this.blockNodeHashmap.put(blockHashKey, blockNode);

      //Return resulting blockNode
      return blockNode;
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
       //*********************Validity Checks***************************************************
       //Checking if block is falsely claims to be genisis block
       byte[] bPrevBlockHash = b.getPrevBlockHash();
       if (bPrevBlockHash == null) {
         return false;
       }
       //Check if claimed parent is valid
       String newBPrevBlockHash = Base64.getEncoder().encodeToString(bPrevBlockHash);
       if (blockNodeHashmap.containsKey(newBPrevBlockHash) == false) {
         return false;
       }

      //Check if height is valid
       BlockNode prevBlock = blockNodeHashmap.get(newBPrevBlockHash);
       int blockHeight = prevBlock.height + 1;
       if (blockHeight <= (maxHeightBlock.height - CUT_OFF_AGE)) {
         return false;
       }

      //Validating Transactions
       UTXOPool prevBlockUTXOPool = prevBlock.getUTXOPoolCopy();
       TxHandler txHandler = new TxHandler(prevBlockUTXOPool);
       ///Converting ArrayList to Array
       ArrayList<Transaction> blockTransactions = b.getTransactions();
       Transaction[] blockTransactionsArray = blockTransactions.toArray(new Transaction[0]);
       ///Checking to see if any transactions were deemed invalid by TxHandler
       Transaction[] validatedTransactions = txHandler.handleTxs(blockTransactionsArray);
       if(blockTransactionsArray.length != validatedTransactions.length) {
         return false;
       }
       //******************************************************************************************

      //Removing validated transactions from TransactionPool
      for (int i = 0; i < validatedTransactions.length; i++) {
         byte[] vTxHash = validatedTransactions[i].getHash();
         this.transactionPool.removeTransaction(vTxHash);
      }

       //Getting up-to-date UTXOPool
       UTXOPool utxoPool = txHandler.getUTXOPool();
       BlockNode blockNode = addBlockNode(b, prevBlock, utxoPool);

       //Re-evalueating maxHeightBlock
      if (blockNode.height > maxHeightBlock.height) {
         maxHeightBlock = blockNode;
      }
      
      //Returning true if block successfully added
      return true;
   }

   /* Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      transactionPool.addTransaction(tx);
      
   }
}
