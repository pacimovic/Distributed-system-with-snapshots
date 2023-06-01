package servent.message.snapshot;

import app.ServentInfo;
import servent.message.BasicMessage;
import servent.message.MessageType;

public class AVTerminateMessage extends BasicMessage {

    public AVTerminateMessage(ServentInfo sender, ServentInfo reciever) {
        super(MessageType.AV_TERMINATE, sender, reciever);
    }
}
