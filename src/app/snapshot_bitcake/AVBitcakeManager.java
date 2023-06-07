package app.snapshot_bitcake;

import app.AppConfig;
import app.CausalBroadcastShared;
import app.ServentInfo;
import servent.message.Message;
import servent.message.snapshot.ABTokenMessage;
import servent.message.snapshot.AVDoneMessage;
import servent.message.snapshot.AVTokenMessage;
import servent.message.util.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class AVBitcakeManager implements BitcakeManager{

    private final AtomicInteger currentAmount = new AtomicInteger(1000);
    public AtomicInteger snapshotAmount = new AtomicInteger();
    public AtomicBoolean snapshotFlag = new AtomicBoolean(false);

    @Override
    public void takeSomeBitcakes(int amount) {
        currentAmount.getAndAdd(-amount);
    }
    @Override
    public void addSomeBitcakes(int amount) {
        currentAmount.getAndAdd(amount);
    }
    @Override
    public int getCurrentBitcakeAmount() {
        return currentAmount.get();
    }

    private Map<Integer, Integer> sentHistory = new ConcurrentHashMap<>();
    private Map<Integer, Integer> recordHistory = new ConcurrentHashMap<>();

    //poruke koje smo poslali sa ovog serventa(potrebno kod sendEffect)
    private static List<Message> sentMessages = new CopyOnWriteArrayList<>();



    public AVBitcakeManager() {
        CausalBroadcastShared.initializeBitcakeManager(this);
    }


    public void tokenEvent(SnapshotCollector snapshotCollector){
        //ovde se pise logika kada mi iniciramo snapshot

        //ovde cuvamo trenutnu vrednost bitcake-ova koju cemo da menjamo po potrebi izmedju DONE i TERMINATE
        snapshotAmount.getAndAdd(getCurrentBitcakeAmount());

        //inicijalizujemo stanje svih kanala na praznu vrednost
        for(ServentInfo servent: AppConfig.getServentInfoList()){
            sentHistory.put(servent.getId(), 0);
            recordHistory.put(servent.getId(), 0);
        }

        //postavimo flag da je pocelo snimanje
        synchronized (AppConfig.snapshotLock){
            snapshotFlag.getAndSet(true);
        }

        //dodamo DONE poruku kod nas
        Message doneMessage = new AVDoneMessage(AppConfig.myServentInfo, AppConfig.myServentInfo);
        snapshotCollector.addAVDoneMessage(AppConfig.myServentInfo.getId(), doneMessage);



        Message tokenMessage = null;
        synchronized (AppConfig.vectorClockLock){
            Map<Integer, Integer> myClock = new ConcurrentHashMap<Integer, Integer>(CausalBroadcastShared.getVectorClock());
            //Svim porukama dajemo nas vektorski sat
            //Komitujemo token poruku kod nas i uvecamo vektorski sat
            tokenMessage = new AVTokenMessage(AppConfig.myServentInfo, AppConfig.myServentInfo, AppConfig.myServentInfo.getId(), myClock);
            CausalBroadcastShared.commitCausalMessage(tokenMessage);
        }


        //posalji isti token svima!!!
        for(Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
            tokenMessage = tokenMessage.changeReceiver(neighbor);

            MessageUtil.sendMessage(tokenMessage);
        }


    }


    private class MapValueUpdater implements BiFunction<Integer, Integer, Integer> {

        private int valueToAdd;

        public MapValueUpdater(int valueToAdd) {
            this.valueToAdd = valueToAdd;
        }

        @Override
        public Integer apply(Integer key, Integer oldValue) {
            return oldValue + valueToAdd;
        }
    }

    public void recordSentTransaction(int neighbor, int amount){
        sentHistory.compute(neighbor, new MapValueUpdater(amount));
    }

    public void recordRecordTransaction(int neighbor, int amount){
        recordHistory.compute(neighbor, new MapValueUpdater(amount));
    }

    public List<Message> getSentMessages() {
        List<Message> toReturn = new CopyOnWriteArrayList<>(sentMessages);
        return toReturn;
    }

    public void addSentMessages(Message message){
        sentMessages.add(message);
    }

    public Map<Integer, Integer> getSentHistory() {
        Map<Integer, Integer> toReturn = new ConcurrentHashMap<>(sentHistory);
        return toReturn;
    }

    public Map<Integer, Integer> getRecordHistory() {
        Map<Integer, Integer> toReturn = new ConcurrentHashMap<>(recordHistory);
        return toReturn;
    }

    public void setSentHistory(Map<Integer, Integer> sentHistory) {
        this.sentHistory = sentHistory;
    }

    public void setRecordHistory(Map<Integer, Integer> recordHistory) {
        this.recordHistory = recordHistory;
    }
}
