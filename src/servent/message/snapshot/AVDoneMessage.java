package servent.message.snapshot;

import app.ServentInfo;
import servent.message.BasicMessage;
import servent.message.MessageType;

public class AVDoneMessage extends BasicMessage {
    public AVDoneMessage(ServentInfo sender, ServentInfo reciever) {
        super(MessageType.AV_DONE, sender, reciever);
    }
}
