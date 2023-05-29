package servent.message.snapshot;

import app.ServentInfo;
import app.snapshot_bitcake.ABSnapshotResult;
import servent.message.BasicMessage;
import servent.message.Message;
import servent.message.MessageType;

import java.util.List;

public class ABTellMessage extends BasicMessage {

    private ABSnapshotResult abSnapshotResult;

    public ABTellMessage(ServentInfo originalSenderInfo, ServentInfo receiverInfo, ABSnapshotResult abSnapshotResult) {
        super(MessageType.AB_TELL, originalSenderInfo, receiverInfo);
        this.abSnapshotResult = abSnapshotResult;
    }

    private ABTellMessage(MessageType messageType, ServentInfo sender, ServentInfo reciever,
                          boolean white, List<ServentInfo> routeList, String messageText,
                          int messageId, ABSnapshotResult abSnapshotResult){
        super(messageType, sender, reciever, white, routeList, messageText, messageId);
        this.abSnapshotResult = abSnapshotResult;
    }

    public ABSnapshotResult getAbSnapshotResult() {
        return abSnapshotResult;
    }

    @Override
    public Message setRedColor() {
        Message toReturn = new ABTellMessage(getMessageType(), getOriginalSenderInfo(), getReceiverInfo(),
                false, getRoute(), getMessageText(), getMessageId(), getAbSnapshotResult());
        return toReturn;
    }

}
