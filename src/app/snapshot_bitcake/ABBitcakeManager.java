package app.snapshot_bitcake;

import app.AppConfig;
import app.CausalBroadcastShared;
import app.ServentInfo;
import servent.message.Message;
import servent.message.snapshot.ABTellMessage;
import servent.message.snapshot.ABTokenMessage;
import servent.message.util.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ABBitcakeManager implements BitcakeManager{

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

    public ABBitcakeManager(){
        for(ServentInfo servent: AppConfig.getServentInfoList()){
            sentHistory.put(servent.getId(), 0);
            recordHistory.put(servent.getId(), 0);
        }
    }

    public int recordedAmount = 0;

    public void tokenEvent(int collectorId, SnapshotCollector snapshotCollector){
        synchronized (AppConfig.colorLock){
            AppConfig.isWhite.set(false);
            recordedAmount = getCurrentBitcakeAmount();

            ABSnapshotResult snapshotResult = new ABSnapshotResult(AppConfig.myServentInfo.getId(),
                    recordedAmount, sentHistory, recordHistory);

            if(collectorId == AppConfig.myServentInfo.getId()) {
                snapshotCollector.addABSnapshotInfo(AppConfig.myServentInfo.getId(),
                        snapshotResult);
            }
            else{
                //napravi ABTell message i posalji
                //za sad direktno inicijatoru
                Message tellMessage = new ABTellMessage(AppConfig.myServentInfo,
                        AppConfig.getInfoById(collectorId), snapshotResult);

                MessageUtil.sendMessage(tellMessage);
            }

            //Svim porukama dajemo nas vektorski sat
            Map<Integer, Integer> myClock = new ConcurrentHashMap<Integer, Integer>();
            for (Map.Entry<Integer, Integer> entry : CausalBroadcastShared.getVectorClock().entrySet()) {
                myClock.put(entry.getKey(), entry.getValue());
            }

            //Komitujemo poruku kod nas i uvecamo vektorski sat
            Message tokenMessage = new ABTokenMessage(AppConfig.myServentInfo, null, collectorId, myClock);
            CausalBroadcastShared.commitCausalMessage(tokenMessage);

            //posalji isti token svima!!!
            for(Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
                tokenMessage = tokenMessage.changeReceiver(neighbor);

                MessageUtil.sendMessage(tokenMessage);
            }

        }
    }
}
