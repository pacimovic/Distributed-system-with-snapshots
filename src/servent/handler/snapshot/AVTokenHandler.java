package servent.handler.snapshot;

import app.AppConfig;
import app.CausalBroadcastShared;
import app.ServentInfo;
import app.snapshot_bitcake.AVBitcakeManager;
import app.snapshot_bitcake.BitcakeManager;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.snapshot.AVDoneMessage;
import servent.message.util.MessageUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AVTokenHandler implements MessageHandler {

    private Message clientMessage;

    private BitcakeManager bitcakeManager;
    private boolean doRebroadcast = false;

    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public AVTokenHandler(Message clientMessage, BitcakeManager bitcakeManager, boolean doRebroadcast) {
        this.clientMessage = clientMessage;
        this.bitcakeManager = bitcakeManager;
        this.doRebroadcast = doRebroadcast;
    }

    @Override
    public void run() {
        if(clientMessage.getMessageType() == MessageType.AV_TOKEN){
            /*
            Stigne nam token
            Rebroadcastujemo je svim komsijama
            Zapamtimo koliko imamo bitcake-ova(pocinje snimanje)
            Saljemo DONE poruku inicijatoru
            Token poruku direktno komitujemo i uvecamo vector clock
             */

            if(doRebroadcast){
                if(clientMessage.getOriginalSenderInfo().getId() == AppConfig.myServentInfo.getId()){
                    AppConfig.timestampedStandardPrint("Got own message back. No rebroadcast.");
                }
                else{
                    //Try to put in the set. Thread safe add ftw.
                    boolean didPut = receivedBroadcasts.add(clientMessage);

                    if(didPut){
                        AppConfig.timestampedStandardPrint("Rebroadcasting... " + receivedBroadcasts.size());

                        //rebroadcast-uj svim komsijama
                        for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
                            //Same message, different receiver, and add us to the route table.
                            MessageUtil.sendMessage(clientMessage.changeReceiver(neighbor).makeMeASender());
                        }

                        //zapamtimo trenutnu vrednost bitcake-ova
                        AVBitcakeManager avBitcakeManager = (AVBitcakeManager) bitcakeManager;
                        avBitcakeManager.snapshotAmount.getAndAdd(avBitcakeManager.getCurrentBitcakeAmount());

                        //inicijalizujemo stanje svih kanala na praznu vrednost
                        Map<Integer, Integer> sentHistory = new ConcurrentHashMap<>();
                        Map<Integer, Integer> recordHistory = new ConcurrentHashMap<>();
                        for(ServentInfo servent: AppConfig.getServentInfoList()){
                            sentHistory.put(servent.getId(), 0);
                            recordHistory.put(servent.getId(), 0);
                        }
                        avBitcakeManager.setSentHistory(sentHistory);
                        avBitcakeManager.setRecordHistory(recordHistory);

                        //ukljucimo flag za pocetak snimanja
                        synchronized (AppConfig.snapshotLock){
                            avBitcakeManager.snapshotFlag.getAndSet(true);
                        }

                        //Saljemo DONE poruku direktno inicijatoru
                        int collectorId = Integer.parseInt(clientMessage.getMessageText());
                        Message doneMessage = new AVDoneMessage(AppConfig.myServentInfo, AppConfig.getInfoById(collectorId));
                        MessageUtil.sendMessage(doneMessage);

                        //Token poruku dodamo u pending messages i komitujemo ako moze
                        CausalBroadcastShared.addPendingMessage(clientMessage);
                        CausalBroadcastShared.checkPendingMessages();
                    }
                    else{
                        AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
                    }
                }
            }


        }
        else{
            AppConfig.timestampedErrorPrint("AVToken handler got: " + clientMessage);
        }
    }
}
