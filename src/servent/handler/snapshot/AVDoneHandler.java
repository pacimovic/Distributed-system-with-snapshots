package servent.handler.snapshot;

import app.AppConfig;
import app.snapshot_bitcake.SnapshotCollector;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;

public class AVDoneHandler implements MessageHandler {

    private Message clientMessage;
    private SnapshotCollector snapshotCollector;

    public AVDoneHandler(Message clientMessage, SnapshotCollector snapshotCollector) {
        this.clientMessage = clientMessage;
        this.snapshotCollector = snapshotCollector;
    }

    @Override
    public void run() {
        if(clientMessage.getMessageType() == MessageType.AV_DONE){
            /*
            Ubacujemo DONE poruku u mapu snapshotCollectora
             */
            snapshotCollector.addAVDoneMessage(clientMessage.getOriginalSenderInfo().getId(), clientMessage);
        }
        else{
            AppConfig.timestampedErrorPrint("Done handler got: " + clientMessage);
        }

    }
}
