package app.snapshot_bitcake;

import app.Cancellable;
import servent.message.Message;

/**
 * Describes a snapshot collector. Made not-so-flexibly for readability.
 * 
 * @author bmilojkovic
 *
 */
public interface SnapshotCollector extends Runnable, Cancellable {

	BitcakeManager getBitcakeManager();

	void addNaiveSnapshotInfo(String snapshotSubject, int amount);

	void addLYSnapshotInfo(int id, LYSnapshotResult lySnapshotResult);

	void addABSnapshotInfo(int id, ABSnapshotResult abSnapshotResult);

	void addAVDoneMessage(int id, Message message);

	void startCollecting();

}