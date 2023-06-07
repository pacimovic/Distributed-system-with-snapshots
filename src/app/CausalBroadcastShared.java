package app;

import app.snapshot_bitcake.ABBitcakeManager;
import app.snapshot_bitcake.AVBitcakeManager;
import app.snapshot_bitcake.BitcakeManager;
import app.snapshot_bitcake.LaiYangBitcakeManager;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.TransactionMessage;
import servent.message.snapshot.ABTokenMessage;
import servent.message.snapshot.AVTokenMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

public class CausalBroadcastShared {

    private static Map<Integer, Integer> vectorClock = new ConcurrentHashMap<>();
    private static Map<Integer, Integer> vectorClockCopy = new ConcurrentHashMap<>();
    private static List<Message> commitedCausalMessageList = new CopyOnWriteArrayList<>();
    private static Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private static Object pendingMessagesLock = new Object();
    private static BitcakeManager bitcakeManager = null;


    public static void initializeBitcakeManager(BitcakeManager bitcakeManagerCausal){
        bitcakeManager = bitcakeManagerCausal;
    }

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

    public static Map<Integer, Integer> getVectorClockCopy() {
        return vectorClockCopy;
    }


    public static List<Message> getCommitedCausalMessages() {
        List<Message> toReturn = new CopyOnWriteArrayList<>(commitedCausalMessageList);
        return toReturn;
    }

    public static void addPendingMessage(Message msg) {
        pendingMessages.add(msg);
        if(msg.getMessageType() == MessageType.AV_TOKEN){
            AVTokenMessage avTokenMessage = (AVTokenMessage) msg;
            vectorClockCopy = new ConcurrentHashMap<>(avTokenMessage.getSenderVectorClock());

            vectorClockCopy.computeIfPresent(avTokenMessage.getOriginalSenderInfo().getId(), new BiFunction<Integer, Integer, Integer>() {
                @Override
                public Integer apply(Integer key, Integer oldValue) {
                    return oldValue+1;
                }
            });
        }
    }

    public static void commitCausalMessage(Message newMessage) {
        AppConfig.timestampedStandardPrint("Committing " + newMessage);
        commitedCausalMessageList.add(newMessage);
        incrementClock(newMessage.getOriginalSenderInfo().getId());

        if(newMessage.getMessageType() == MessageType.AV_TOKEN){
            //napravi kopiju casovnika te poruke i uvecaj za 1
            AVTokenMessage avTokenMessage = (AVTokenMessage) newMessage;
            vectorClockCopy = new ConcurrentHashMap<>(avTokenMessage.getSenderVectorClock());

            vectorClockCopy.computeIfPresent(avTokenMessage.getOriginalSenderInfo().getId(), new BiFunction<Integer, Integer, Integer>() {
                @Override
                public Integer apply(Integer key, Integer oldValue) {
                    return oldValue+1;
                }
            });
        }

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

    private static boolean otherClockLesser(Map<Integer, Integer> clock1, Map<Integer, Integer> clock2) {
        if (clock1.size() != clock2.size()) {
            throw new IllegalArgumentException("Clocks are not same size how why");
        }
        for(int i = 0; i < clock1.size(); i++) {
            if (clock2.get(i) < clock1.get(i)) {
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

                    //ako nije transakcija samo uvecamo vector clock
                    if(pendingMessage.getMessageType() == MessageType.AV_TOKEN){
                        AVTokenMessage avTokenMessage = (AVTokenMessage) pendingMessage;
                        if(!otherClockGreater(myVectorClock, avTokenMessage.getSenderVectorClock())){
                            gotWork = true;

                            AppConfig.timestampedStandardPrint("Committing " + pendingMessage);
                            commitedCausalMessageList.add(pendingMessage);
                            incrementClock(pendingMessage.getOriginalSenderInfo().getId());
                            iterator.remove();

                            break;
                        }
                        else{
                            //System.out.println("Other clock is greater!(new message) Please wait...");
                            continue;
                        }
                    }
                    if(pendingMessage.getMessageType() == MessageType.AB_TOKEN){
                        ABTokenMessage abTokenMessage = (ABTokenMessage) pendingMessage;
                        if(!otherClockGreater(myVectorClock, abTokenMessage.getSenderVectorClock())){
                            gotWork = true;

                            AppConfig.timestampedStandardPrint("Committing " + pendingMessage);
                            commitedCausalMessageList.add(pendingMessage);
                            incrementClock(pendingMessage.getOriginalSenderInfo().getId());
                            iterator.remove();

                            break;
                        }
                        else{
                            continue;
                        }
                    }


                    TransactionMessage transactionMessage = (TransactionMessage) pendingMessage;

                    if (!otherClockGreater(myVectorClock, transactionMessage.getSenderVectorClock())) {
                        gotWork = true;

                        /*
                        Ovde ubacujemo logiku za dodavanje bitcake-ova ukoliko je TRANSAKCIJA
                         */


                        //dodaj bitcake-ove i zabelezi u istoriju dobijenih transakcija
                        String amountString = transactionMessage.getMessageText();

                        int amountNumber = 0;
                        try {
                            amountNumber = Integer.parseInt(amountString);
                        } catch (NumberFormatException e) {
                            AppConfig.timestampedErrorPrint("Couldn't parse amount: " + amountString);
                            return;
                        }
                        bitcakeManager.addSomeBitcakes(amountNumber);

                        
                        if (bitcakeManager instanceof ABBitcakeManager) {
                            ABBitcakeManager abBitcakeManager = (ABBitcakeManager) bitcakeManager;
                            //zabelezimo u istoriju dobijenih transakcija kolicinu od originalnog posiljaoca
                            abBitcakeManager.recordRecordTransaction(transactionMessage.getOriginalSenderInfo().getId(), amountNumber);
                        }
                        if(bitcakeManager instanceof AVBitcakeManager){
                            AVBitcakeManager avBitcakeManager = (AVBitcakeManager) bitcakeManager;
                            synchronized (AppConfig.snapshotLock){
                                if(avBitcakeManager.snapshotFlag.get() == true){
                                    Map<Integer, Integer> myVectorClockCopy = getVectorClockCopy();
                                    if(otherClockLesser(myVectorClockCopy, transactionMessage.getSenderVectorClock())){
                                        //stara poruka ukljuci je u snapshot
                                        avBitcakeManager.snapshotAmount.getAndAdd(amountNumber);
                                        avBitcakeManager.recordRecordTransaction(transactionMessage.getOriginalSenderInfo().getId(), amountNumber);
                                        System.out.println("stara poruka ukljuci je u snapshot");
                                    }
                                    else{
                                        //poruka je krenula nakon snapshota, znaci nova je.
                                        System.out.println("poruka je krenula nakon snapshota, znaci nova je.");
                                    }
                                }
                            }

                        }

                        AppConfig.timestampedStandardPrint("Committing " + pendingMessage);
                        commitedCausalMessageList.add(pendingMessage);
                        incrementClock(pendingMessage.getOriginalSenderInfo().getId());

                        iterator.remove();

                        break;
                    }
                    else{
//                        System.out.println("Other clock is greater!(new message) Please wait...");
                    }
                }
            }
        }

    }

}
