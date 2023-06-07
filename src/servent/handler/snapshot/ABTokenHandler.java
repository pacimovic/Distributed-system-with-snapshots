package servent.handler.snapshot;

import app.AppConfig;
import app.CausalBroadcastShared;
import app.snapshot_bitcake.ABBitcakeManager;
import app.snapshot_bitcake.ABSnapshotResult;
import app.snapshot_bitcake.BitcakeManager;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.snapshot.ABTellMessage;
import servent.message.util.MessageUtil;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ABTokenHandler implements MessageHandler {

    private Message clientMessage;
    private BitcakeManager bitcakeManager;
    private boolean doRebroadcast = false;

    private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

    public ABTokenHandler(Message clientMessage, BitcakeManager bitcakeManager, boolean doRebroadcast) {
        this.clientMessage = clientMessage;
        this.bitcakeManager = bitcakeManager;
        this.doRebroadcast = doRebroadcast;
    }

    @Override
    public void run() {
        if (clientMessage.getMessageType() == MessageType.AB_TOKEN){
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

                        //Pravimo lokalno stanje, pakujemo u poruku i saljemo je inicijatoru (za sad direktno inicijatoru)
                        ABBitcakeManager abBitcakeManager = (ABBitcakeManager) bitcakeManager;
                        ABSnapshotResult snapshotResult = new ABSnapshotResult(AppConfig.myServentInfo.getId(),
                                abBitcakeManager.getCurrentBitcakeAmount(), abBitcakeManager.getSentHistory(), abBitcakeManager.getRecordHistory());

                        int collectorId = Integer.parseInt(clientMessage.getMessageText());
                        Message tellMessage = new ABTellMessage(AppConfig.myServentInfo, AppConfig.getInfoById(collectorId), snapshotResult);
                        MessageUtil.sendMessage(tellMessage);

                        //Token poruku direktno komitujemo i uvecamo vector clock
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
            AppConfig.timestampedErrorPrint("ABToken handler got: " + clientMessage);
        }
    }
}
