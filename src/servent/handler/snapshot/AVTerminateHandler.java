package servent.handler.snapshot;

import app.AppConfig;
import app.snapshot_bitcake.AVBitcakeManager;
import app.snapshot_bitcake.BitcakeManager;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.util.MessageUtil;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AVTerminateHandler implements MessageHandler {

    private Message clientMessage;
    private BitcakeManager bitcakeManager;
    private boolean doRebroadcast = false;

    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public AVTerminateHandler(Message clientMessage, BitcakeManager bitcakeManager, boolean doRebroadcast) {
        this.clientMessage = clientMessage;
        this.bitcakeManager = bitcakeManager;
        this.doRebroadcast = doRebroadcast;
    }

    @Override
    public void run() {
        /*
        Rebroadcastujemo komsijama
        Prestajemo sa snimanjem kanala
        Ispisujemo svoje stanje
         */

        if(clientMessage.getMessageType() == MessageType.AV_TERMINATE){

            if(doRebroadcast){
                if(clientMessage.getOriginalSenderInfo().getId() == AppConfig.myServentInfo.getId()){
                    AppConfig.timestampedStandardPrint("Got own message back. No rebroadcast.");
                }
                else{
                    boolean didPut = receivedBroadcasts.add(clientMessage);

                    if(didPut){
                        AppConfig.timestampedStandardPrint("Rebroadcasting... " + receivedBroadcasts.size());

                        //rebroadcast-uj svim komsijama
                        for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
                            //Same message, different receiver, and add us to the route table.
                            MessageUtil.sendMessage(clientMessage.changeReceiver(neighbor).makeMeASender());
                        }

                        //iskljucujemo flag za snimanje
                        AVBitcakeManager avBitcakeManager = (AVBitcakeManager) bitcakeManager;
                        synchronized (AppConfig.snapshotLock){
                            avBitcakeManager.snapshotFlag.getAndSet(false);
                        }

                        //ISPIS
                        int sum = 0;
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
                        AppConfig.timestampedStandardPrint("Sent amount: " + sentSum);
                        AppConfig.timestampedStandardPrint("Get amount: " + getSum);

                        AppConfig.timestampedStandardPrint("System bitcake count: " + sum);

                        //reset
                        avBitcakeManager.snapshotAmount.set(0);
                    }
                    else{
                        AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
                    }
                }
            }




        }
        else{
            AppConfig.timestampedErrorPrint("AVTerminate handler got: " + clientMessage);
        }

    }
}
