package app.snapshot_bitcake;

import servent.message.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class AVBitcakeManager implements BitcakeManager{

    private final AtomicInteger currentAmount = new AtomicInteger(1000);

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
    }

    public void tokenEvent(){
        //ovde se pise logika kada mi iniciramo snapshot
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
        sentHistory.compute(neighbor, new AVBitcakeManager.MapValueUpdater(amount));
    }

    public void recordRecordTransaction(int neighbor, int amount){
        recordHistory.compute(neighbor, new AVBitcakeManager.MapValueUpdater(amount));
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
}
