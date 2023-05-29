package servent.message.snapshot;

import app.AppConfig;
import app.ServentInfo;
import servent.message.BasicMessage;
import servent.message.Message;
import servent.message.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ABTokenMessage extends BasicMessage {

    private Map<Integer, Integer> senderVectorClock;

    public ABTokenMessage(ServentInfo sender, ServentInfo reciever, int collectorId,
                          Map<Integer, Integer> senderVectorClock){
        super(MessageType.AB_TOKEN, sender, reciever, String.valueOf(collectorId));
        this.senderVectorClock = senderVectorClock;
    }

    private ABTokenMessage(MessageType messageType, ServentInfo sender, ServentInfo reciever,
                           boolean white, List<ServentInfo> routeList, String messageText, int messageId,
                           Map<Integer, Integer> senderVectorClock){
        super(messageType, sender, reciever, white, routeList, messageText, messageId);
        this.senderVectorClock = senderVectorClock;
    }

    @Override
    public Message makeMeASender() {
        ServentInfo newRouteItem = AppConfig.myServentInfo;

        List<ServentInfo> newRouteList = new ArrayList<>(getRoute());
        newRouteList.add(newRouteItem);
        Message toReturn = new ABTokenMessage(getMessageType(), getOriginalSenderInfo(), getReceiverInfo(),
                isWhite(), newRouteList, getMessageText(), getMessageId(), getSenderVectorClock());
        return toReturn;
    }

    @Override
    public Message changeReceiver(Integer newReceiverId) {
        if(AppConfig.myServentInfo.getNeighbors().contains(newReceiverId)){
            ServentInfo newRecieverInfo = AppConfig.getInfoById(newReceiverId);

            Message toReturn = new ABTokenMessage(getMessageType(), getOriginalSenderInfo(), newRecieverInfo,
                    isWhite(), getRoute(), getMessageText(), getMessageId(), getSenderVectorClock());
            return toReturn;
        }
        else {
            AppConfig.timestampedErrorPrint("Trying to make a message for " + newReceiverId + " who is not a neighbor.");
            return null;
        }
    }

    public Map<Integer, Integer> getSenderVectorClock() {
        return senderVectorClock;
    }
}
