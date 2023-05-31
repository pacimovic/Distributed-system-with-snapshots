package servent.handler.snapshot;

import app.AppConfig;
import app.snapshot_bitcake.SnapshotCollector;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.snapshot.ABTellMessage;

public class ABTellHandler implements MessageHandler {

    private Message clientMessage;
    private SnapshotCollector snapshotCollector;

    public ABTellHandler(Message clientMessage, SnapshotCollector snapshotCollector) {
        this.clientMessage = clientMessage;
        this.snapshotCollector = snapshotCollector;
    }

    @Override
    public void run() {
        if(clientMessage.getMessageType() == MessageType.AB_TELL){
            ABTellMessage abTellMessage = (ABTellMessage) clientMessage;
            snapshotCollector.addABSnapshotInfo(abTellMessage.getOriginalSenderInfo().getId(),
                    abTellMessage.getAbSnapshotResult());
        }
        else{
            AppConfig.timestampedErrorPrint("Tell amount handler got: " + clientMessage);
        }
    }
}
