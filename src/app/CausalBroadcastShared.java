package app;

import servent.message.Message;
import servent.message.TransactionMessage;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

public class CausalBroadcastShared {

    private static Map<Integer, Integer> vectorClock = new ConcurrentHashMap<>();
    private static List<Message> commitedCausalMessageList = new CopyOnWriteArrayList<>();
    private static Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private static Object pendingMessagesLock = new Object();


    public static void initializeVectorClock(int serventCount) {
        for(int i = 0; i < serventCount; i++) {
            vectorClock.put(i, 0);
        }
    }

    public static void incrementClock(int serventId) {
        vectorClock.computeIfPresent(serventId, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer apply(Integer key, Integer oldValue) {
                return oldValue+1;
            }
        });
    }

    public static Map<Integer, Integer> getVectorClock() {
        return vectorClock;
    }

    public static List<Message> getCommitedCausalMessages() {
        List<Message> toReturn = new CopyOnWriteArrayList<>(commitedCausalMessageList);
        return toReturn;
    }

    public static void addPendingMessage(Message msg) {
        pendingMessages.add(msg);
    }

    public static void  commitCausalMessage(Message newMessage) {
        AppConfig.timestampedStandardPrint("Committing " + newMessage);
        commitedCausalMessageList.add(newMessage);
        incrementClock(newMessage.getOriginalSenderInfo().getId());

        checkPendingMessages();
    }

    private static boolean otherClockGreater(Map<Integer, Integer> clock1, Map<Integer, Integer> clock2) {
        if (clock1.size() != clock2.size()) {
            throw new IllegalArgumentException("Clocks are not same size how why");
        }
        for(int i = 0; i < clock1.size(); i++) {
            if (clock2.get(i) > clock1.get(i)) {
                return true;
            }
        }
        return false;
    }

    public static void checkPendingMessages() {
        boolean gotWork = true;

        while (gotWork) {
            gotWork = false;

            synchronized (pendingMessagesLock) {
                Iterator<Message> iterator = pendingMessages.iterator();

                Map<Integer, Integer> myVectorClock = getVectorClock();
                while (iterator.hasNext()) {
                    Message pendingMessage = iterator.next();
                    TransactionMessage transactionMessage = (TransactionMessage) pendingMessage;

                    if (!otherClockGreater(myVectorClock, transactionMessage.getSenderVectorClock())) {
                        gotWork = true;

                        /*
                        Ovde ubacujemo logiku za dodavanje bitcake-ova ukoliko je TRANSAKCIJA  ,
                        i markiranje serventa ukoliko je MARKER
                         */


                        AppConfig.timestampedStandardPrint("Committing " + pendingMessage);
                        commitedCausalMessageList.add(pendingMessage);
                        incrementClock(pendingMessage.getOriginalSenderInfo().getId());

                        iterator.remove();

                        break;
                    }
                }
            }
        }

    }

}
