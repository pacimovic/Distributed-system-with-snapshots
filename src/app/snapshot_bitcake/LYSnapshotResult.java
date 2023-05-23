package app.snapshot_bitcake;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LYSnapshotResult implements Serializable {

    private static final long serialVersionUID = 8939516333227254439L;

    private final int serventId;
    private final int recordedAmount;
    private final Map<Integer, Integer> giveHistory;
    private final Map<Integer, Integer> getHistory;


    public LYSnapshotResult(int serventId, int recordedAmount,
                            Map<Integer, Integer> giveHistory, Map<Integer, Integer> getHistory) {
        this.serventId = serventId;
        this.recordedAmount = recordedAmount;
        this.giveHistory = new ConcurrentHashMap<>(giveHistory);
        this.getHistory = new ConcurrentHashMap<>(getHistory);
    }


    public int getServentId() {
        return serventId;
    }

    public int getRecordedAmount() {
        return recordedAmount;
    }

    public Map<Integer, Integer> getGiveHistory() {
        return giveHistory;
    }

    public Map<Integer, Integer> getGetHistory() {
        return getHistory;
    }
}
