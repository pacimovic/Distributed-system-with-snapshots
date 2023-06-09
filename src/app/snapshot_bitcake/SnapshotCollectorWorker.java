package app.snapshot_bitcake;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import app.AppConfig;
import app.CausalBroadcastShared;
import servent.message.Message;
import servent.message.snapshot.AVTerminateMessage;
import servent.message.util.MessageUtil;

/**
 * Main snapshot collector class. Has support for Naive, Chandy-Lamport
 * and Lai-Yang snapshot algorithms.
 *
 * @author bmilojkovic
 *
 */
public class SnapshotCollectorWorker implements SnapshotCollector {

	private volatile boolean working = true;

	private AtomicBoolean collecting = new AtomicBoolean(false);

	private Map<String, Integer> collectedNaiveValues = new ConcurrentHashMap<>();
	private Map<Integer, LYSnapshotResult> collectedLYValues = new ConcurrentHashMap<>();
	private Map<Integer, ABSnapshotResult> collectedABValues = new ConcurrentHashMap<>();
	private Map<Integer, Message> doneAVMessages = new ConcurrentHashMap<>();

	private SnapshotType snapshotType;

	private BitcakeManager bitcakeManager;

	public SnapshotCollectorWorker(SnapshotType snapshotType) {
		this.snapshotType = snapshotType;
		switch(snapshotType) {
			case LAI_YANG:
				bitcakeManager = new LaiYangBitcakeManager();
				break;
			case ACHARYA_BADRINATH:
				bitcakeManager = new ABBitcakeManager();
				break;
			case ALAGAR_VENKATESAN:
				bitcakeManager = new AVBitcakeManager();
				break;
			case NONE:
				AppConfig.timestampedErrorPrint("Making snapshot collector without specifying type. Exiting...");
				System.exit(0);
		}
	}

	@Override
	public BitcakeManager getBitcakeManager() {
		return bitcakeManager;
	}

	@Override
	public void run() {
		while(working) {

			/*
			 * Not collecting yet - just sleep until we start actual work, or finish
			 */
			while (collecting.get() == false) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {

					e.printStackTrace();
				}

				if (working == false) {
					return;
				}
			}

			/*
			 * Collecting is done in three stages:
			 * 1. Send messages asking for values
			 * 2. Wait for all the responses
			 * 3. Print result
			 */

			//1 send asks
			switch (snapshotType) {
				case LAI_YANG:
					((LaiYangBitcakeManager)bitcakeManager).markerEvent(AppConfig.myServentInfo.getId(), this);
					break;
				case ACHARYA_BADRINATH:
					((ABBitcakeManager)bitcakeManager).tokenEvent(AppConfig.myServentInfo.getId(), this);
					break;
				case ALAGAR_VENKATESAN:
					((AVBitcakeManager)bitcakeManager).tokenEvent(this);
				case NONE:
					//Shouldn't be able to come here. See constructor.
					break;
			}

			//2 wait for responses or finish
			boolean waiting = true;
			while (waiting) {
				switch (snapshotType) {
					case LAI_YANG:
						if (collectedLYValues.size() == AppConfig.getServentCount()) {
							waiting = false;
						}
						break;
					case ACHARYA_BADRINATH:
						if(collectedABValues.size() == AppConfig.getServentCount()) {
							waiting = false;
						}
						break;
					case ALAGAR_VENKATESAN:
						if(doneAVMessages.size() == AppConfig.getServentCount()){
							waiting = false;
						}
						break;
					case NONE:
						//Shouldn't be able to come here. See constructor.
						break;
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (working == false) {
					return;
				}
			}

			//print
			int sum;
			switch (snapshotType) {
				case NAIVE:
					sum = 0;
					for (Entry<String, Integer> itemAmount : collectedNaiveValues.entrySet()) {
						sum += itemAmount.getValue();
						AppConfig.timestampedStandardPrint(
								"Info for " + itemAmount.getKey() + " = " + itemAmount.getValue() + " bitcake");
					}

					AppConfig.timestampedStandardPrint("System bitcake count: " + sum);

					collectedNaiveValues.clear(); //reset for next invocation
					break;

				case LAI_YANG:
					sum = 0;
					for (Entry<Integer, LYSnapshotResult> nodeResult : collectedLYValues.entrySet()) {
						sum += nodeResult.getValue().getRecordedAmount();
						AppConfig.timestampedStandardPrint(
								"Recorded bitcake amount for " + nodeResult.getKey() + " = " + nodeResult.getValue().getRecordedAmount());
					}
					for(int i = 0; i < AppConfig.getServentCount(); i++) {
						for (int j = 0; j < AppConfig.getServentCount(); j++) {
							if (i != j) {

								int ijAmount = collectedLYValues.get(i).getGiveHistory().get(j);
								int jiAmount = collectedLYValues.get(j).getGetHistory().get(i);

								if (ijAmount != jiAmount) {
									String outputString = String.format(
											"Unreceived bitcake amount: %d from servent %d to servent %d",
											ijAmount - jiAmount, i, j);
									AppConfig.timestampedStandardPrint(outputString);
									sum += ijAmount - jiAmount;
								}
							}
						}
					}

					AppConfig.timestampedStandardPrint("System bitcake count: " + sum);

					collectedLYValues.clear(); //reset for next invocation
					break;

				case ACHARYA_BADRINATH:
					sum = 0;
					for (Entry<Integer, ABSnapshotResult> nodeResult : collectedABValues.entrySet()) {

						sum += nodeResult.getValue().getRecordedAmount();
						AppConfig.timestampedStandardPrint(
								"Recorded bitcake amount for " + nodeResult.getKey() + " = " + nodeResult.getValue().getRecordedAmount());
//						System.out.println("History for " + nodeResult.getKey() + " = sentHistory: " + nodeResult.getValue().getSentHistory() +
//								" , recordHistory: " + nodeResult.getValue().getRecordHistory());
					}
					for(int i = 0; i < AppConfig.getServentCount(); i++) {
						for (int j = 0; j < AppConfig.getServentCount(); j++) {
							if (i != j) {
								int ijAmount = collectedABValues.get(i).getSentHistory().get(j);
								int jiAmount = collectedABValues.get(j).getRecordHistory().get(i);

								if (ijAmount != jiAmount) {
									String outputString = String.format(
											"Unreceived bitcake amount: %d from servent %d to servent %d",
											ijAmount - jiAmount, i, j);
//									System.out.println(j + " recieve history: " + collectedABValues.get(j).getRecordHistory());
									AppConfig.timestampedStandardPrint(outputString);
									sum += ijAmount - jiAmount;
								}
							}
						}
					}
					AppConfig.timestampedStandardPrint("System bitcake count: " + sum);

					collectedABValues.clear(); //reset for next invocation

					break;
				case ALAGAR_VENKATESAN:
                    /*
					Prestajemo sa snimanjem kanala
					Broadcastujemo TERMINATE poruku svima
					Ispisujemo svoje stanje
					 */

					//prestajemo sa snimanjem
					AVBitcakeManager avBitcakeManager = (AVBitcakeManager) bitcakeManager;
					synchronized (AppConfig.snapshotLock){
						avBitcakeManager.snapshotFlag.getAndSet(false);
					}


					//saljemo svima TERMINATE da ugase snimanje
					Message terminateMessage = new AVTerminateMessage(AppConfig.myServentInfo, AppConfig.myServentInfo);
					for(Integer neighbor: AppConfig.myServentInfo.getNeighbors()){
						MessageUtil.sendMessage(terminateMessage.changeReceiver(neighbor));
					}

					//ISPIS
					sum = 0;
					int myId = AppConfig.myServentInfo.getId();
					AppConfig.timestampedStandardPrint(
							"Recorded bitcake amount for " + myId + " = " + avBitcakeManager.snapshotAmount.get());
					sum += avBitcakeManager.snapshotAmount.get();

					int sentSum=0, getSum=0;
					for(int i = 0; i < AppConfig.getServentCount(); i++){
						if(i != AppConfig.myServentInfo.getId()){
							sentSum += avBitcakeManager.getSentHistory().get(i);
							getSum += avBitcakeManager.getRecordHistory().get(i);
						}
					}
					//dodamo zakasnele poruke na sumu
					sum += getSum;

					AppConfig.timestampedStandardPrint("Sent amount: " + sentSum);
					AppConfig.timestampedStandardPrint("Get amount: " + getSum);

					AppConfig.timestampedStandardPrint("System bitcake count: " + sum);

					//reset
					avBitcakeManager.snapshotAmount.set(0);

					break;
				case NONE:
					//Shouldn't be able to come here. See constructor.
					break;
			}
			collecting.set(false);
		}

	}

	@Override
	public void addNaiveSnapshotInfo(String snapshotSubject, int amount) {
		collectedNaiveValues.put(snapshotSubject, amount);
	}

	@Override
	public void addLYSnapshotInfo(int id, LYSnapshotResult lySnapshotResult) {
		collectedLYValues.put(id, lySnapshotResult);
	}

	@Override
	public void addABSnapshotInfo(int id, ABSnapshotResult abSnapshotResult) {
		collectedABValues.put(id, abSnapshotResult);
	}

	@Override
	public void addAVDoneMessage(int id, Message message){
		doneAVMessages.put(id, message);
	}

	@Override
	public void startCollecting() {
		if(this.collecting.get() == true){
			AppConfig.timestampedErrorPrint("Snapshot isn't over yet!");
			return;
		}
		boolean oldValue = this.collecting.getAndSet(true);

		if (oldValue == true) {
			AppConfig.timestampedErrorPrint("Tried to start collecting before finished with previous.");
		}
	}

	@Override
	public void stop() {
		working = false;
	}

}
