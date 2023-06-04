package cli.command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import app.AppConfig;
import app.CausalBroadcastShared;
import app.ServentInfo;
import app.snapshot_bitcake.BitcakeManager;
import servent.message.Message;
import servent.message.TransactionMessage;
import servent.message.util.MessageUtil;

public class TransactionBurstCommand implements CLICommand {

	private static final int TRANSACTION_COUNT = 4;
	private static final int BURST_WORKERS = 2;
	private static final int MAX_TRANSFER_AMOUNT = 10;

	
	private BitcakeManager bitcakeManager;
	
	public TransactionBurstCommand(BitcakeManager bitcakeManager) {
		this.bitcakeManager = bitcakeManager;
	}
	
	private class TransactionBurstWorker implements Runnable {
		
		@Override
		public void run() {
			ThreadLocalRandom rand = ThreadLocalRandom.current();


			for (int i = 0; i < TRANSACTION_COUNT; i++) {

				int amount = 1 + rand.nextInt(MAX_TRANSFER_AMOUNT);

				Message transactionMessage = null;
				synchronized (AppConfig.vectorClockLock){
					Map<Integer, Integer> myClock = new ConcurrentHashMap<Integer, Integer>(CausalBroadcastShared.getVectorClock());
					//Komitujemo poruku kod nas i uvecamo vektorski sat
					transactionMessage = new TransactionMessage(
							AppConfig.myServentInfo, AppConfig.myServentInfo, amount, bitcakeManager, myClock);
					CausalBroadcastShared.commitCausalMessage(transactionMessage);
				}


				for (int neighbor : AppConfig.myServentInfo.getNeighbors()) {
					ServentInfo neighborInfo = AppConfig.getInfoById(neighbor);
					
                    transactionMessage = transactionMessage.changeReceiver(neighbor);

					MessageUtil.sendMessage(transactionMessage);
				}
				
			}
		}
	}
	
	@Override
	public String commandName() {
		return "transaction_burst";
	}

	@Override
	public void execute(String args) {
		for (int i = 0; i < BURST_WORKERS; i++) {
			Thread t = new Thread(new TransactionBurstWorker());
			
			t.start();
		}
	}

	
}
