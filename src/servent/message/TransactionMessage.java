package servent.message;

import app.AppConfig;
import app.ServentInfo;
import app.snapshot_bitcake.BitcakeManager;
import app.snapshot_bitcake.LYSnapshotResult;
import app.snapshot_bitcake.LaiYangBitcakeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a bitcake transaction. We are sending some bitcakes to another node.
 * 
 * @author bmilojkovic
 *
 */
public class TransactionMessage extends BasicMessage {

	private static final long serialVersionUID = -333251402058492901L;

	private transient BitcakeManager bitcakeManager;

	private Map<Integer, Integer> senderVectorClock;


	public TransactionMessage(ServentInfo sender, ServentInfo receiver, int amount, BitcakeManager bitcakeManager,
							  Map<Integer, Integer> senderVectorClock) {
		super(MessageType.TRANSACTION, sender, receiver, String.valueOf(amount));
		this.bitcakeManager = bitcakeManager;
		this.senderVectorClock = senderVectorClock;
	}

	private TransactionMessage(MessageType messageType, ServentInfo sender, ServentInfo receiver,
							   boolean white, List<ServentInfo> routeList, String messageText, int messageId,
							   BitcakeManager bitcakeManager ,Map<Integer, Integer> senderVectorClock){
		super(messageType, sender, receiver, white, routeList, messageText, messageId);
		this.bitcakeManager = bitcakeManager;
		this.senderVectorClock = senderVectorClock;
	}

	/**
	 * We want to take away our amount exactly as we are sending, so our snapshots don't mess up.
	 * This method is invoked by the sender just before sending, and with a lock that guarantees
	 * that we are white when we are doing this in Chandy-Lamport.
	 */
	@Override
	public void sendEffect() {
		//Ako nije nasa poruka ne skidamo sebi bitcakeove nego samo posaljemo dalje
		if(getOriginalSenderInfo().getId() != AppConfig.myServentInfo.getId()) return;

		//ako je poruka vec poslata ne oduzimamo opet sebi bitackove od svih servenata!
		LaiYangBitcakeManager bitcakeManagerSentMessages = (LaiYangBitcakeManager) bitcakeManager;
		if(bitcakeManagerSentMessages.getSentMessages().contains(this)) return;
		else bitcakeManagerSentMessages.addSentMessages(this);


        /*
		ako jeste nasa poruka skidamo sebi broj_servenata * kolicina_bitcake-ova,
		jer ce kroz broadcast transakcija stici svakom serventu u grafu.
		Takodje zabelezimo u istoriju da smo dali ovaj amount svim serventima(jer ce ta transakcija do njih stici)
		 */
		int amount = Integer.parseInt(getMessageText()) * (AppConfig.getServentCount() - 1);
		bitcakeManager.takeSomeBitcakes(amount);

		if (bitcakeManager instanceof LaiYangBitcakeManager && isWhite()) {
			LaiYangBitcakeManager lyFinancialManager = (LaiYangBitcakeManager)bitcakeManager;
			for(ServentInfo servent: AppConfig.getServentInfoList()){
				lyFinancialManager.recordGiveTransaction(servent.getId(), Integer.parseInt(getMessageText()));
			}

		}


	}


	@Override
	public Message makeMeASender() {
		ServentInfo newRouteItem = AppConfig.myServentInfo;

		List<ServentInfo> newRouteList = new ArrayList<>(getRoute());
		newRouteList.add(newRouteItem);
		Message toReturn = new TransactionMessage(getMessageType(), getOriginalSenderInfo(),
				getReceiverInfo(), isWhite(), newRouteList, getMessageText(), getMessageId(),
				getBitcakeManager(), getSenderVectorClock());

		return toReturn;
	}

	@Override
	public Message changeReceiver(Integer newReceiverId) {
		if (AppConfig.myServentInfo.getNeighbors().contains(newReceiverId)) {
			ServentInfo newReceiverInfo = AppConfig.getInfoById(newReceiverId);

			Message toReturn = new TransactionMessage(getMessageType(), getOriginalSenderInfo(),
					newReceiverInfo, isWhite(), getRoute(), getMessageText(), getMessageId(),
					getBitcakeManager(), getSenderVectorClock());

			return toReturn;
		} else {
			AppConfig.timestampedErrorPrint("Trying to make a message for " + newReceiverId + " who is not a neighbor.");
			return null;
		}
	}

	public Map<Integer, Integer> getSenderVectorClock() {
		return senderVectorClock;
	}

	public BitcakeManager getBitcakeManager() {
		return bitcakeManager;
	}



}
