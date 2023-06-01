package servent.handler.snapshot;

import app.AppConfig;
import app.snapshot_bitcake.BitcakeManager;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;

import java.util.Collections;
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
        }
        else{
            AppConfig.timestampedErrorPrint("AVToken handler got: " + clientMessage);
        }
    }
}
