package app.snapshot_bitcake;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import app.AppConfig;
import app.CausalBroadcastShared;
import servent.message.Message;
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
//								if (AppConfig.getInfoById(i).getNeighbors().contains(j) &&
//										AppConfig.getInfoById(j).getNeighbors().contains(i)) {
								int ijAmount = collectedLYValues.get(i).getGiveHistory().get(j);
								int jiAmount = collectedLYValues.get(j).getGetHistory().get(i);

								if (ijAmount != jiAmount) {
									String outputString = String.format(
											"Unreceived bitcake amount: %d from servent %d to servent %d",
											ijAmount - jiAmount, i, j);
									AppConfig.timestampedStandardPrint(outputString);
									sum += ijAmount - jiAmount;
								}
//								}
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
					}
					for(int i = 0; i < AppConfig.getServentCount(); i++) {
						for (int j = 0; j < AppConfig.getServentCount(); j++) {
							if (i != j) {
//								if (AppConfig.getInfoById(i).getNeighbors().contains(j) &&
//										AppConfig.getInfoById(j).getNeighbors().contains(i)) {
								int ijAmount = collectedABValues.get(i).getSentHistory().get(j);
								int jiAmount = collectedABValues.get(j).getRecordHistory().get(i);

								if (ijAmount != jiAmount) {
									String outputString = String.format(
											"Unreceived bitcake amount: %d from servent %d to servent %d",
											ijAmount - jiAmount, i, j);
									System.out.println(j + " recieve history: " + collectedABValues.get(j).getRecordHistory());
									AppConfig.timestampedStandardPrint(outputString);
									sum += ijAmount - jiAmount;
								}
//								}
							}
						}
					}
					AppConfig.timestampedStandardPrint("System bitcake count: " + sum);

					collectedABValues.clear(); //reset for next invocation

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
		System.out.println(id + ": " + "sent history - " + abSnapshotResult.getSentHistory() +
				" record history - " + abSnapshotResult.getRecordHistory());
	}


	@Override
	public void startCollecting() {
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
