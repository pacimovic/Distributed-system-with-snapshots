package servent.handler;

import app.AppConfig;
import app.ServentInfo;
import app.snapshot_bitcake.BitcakeManager;
import app.snapshot_bitcake.LaiYangBitcakeManager;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.util.MessageUtil;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionHandler implements MessageHandler {

	private Message clientMessage;
	private BitcakeManager bitcakeManager;
	private boolean doRebroadcast = false;

	private static Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());

	public TransactionHandler(Message clientMessage, BitcakeManager bitcakeManager,boolean doRebroadcast) {
		this.clientMessage = clientMessage;
		this.bitcakeManager = bitcakeManager;
		this.doRebroadcast = doRebroadcast;
	}

	@Override
	public void run() {
		if (clientMessage.getMessageType() == MessageType.TRANSACTION) {


			//Rebroadcast
			ServentInfo senderInfo = clientMessage.getOriginalSenderInfo();
			if (doRebroadcast) {
				if (senderInfo.getId() == AppConfig.myServentInfo.getId()) {
					//We are the sender :o someone bounced this back to us. /ignore
					AppConfig.timestampedStandardPrint("Got own message back. No rebroadcast.");
				} else {
					//Try to put in the set. Thread safe add ftw.
					boolean didPut = receivedBroadcasts.add(clientMessage);

					if (didPut) {
						//New message for us. Rebroadcast it.
						AppConfig.timestampedStandardPrint("Rebroadcasting... " + receivedBroadcasts.size());

						//dodaj bitcake-ove i zabelezi u istoriju dobijenih transakcija
						String amountString = clientMessage.getMessageText();

						int amountNumber = 0;
						try {
							amountNumber = Integer.parseInt(amountString);
						} catch (NumberFormatException e) {
							AppConfig.timestampedErrorPrint("Couldn't parse amount: " + amountString);
							return;
						}
						bitcakeManager.addSomeBitcakes(amountNumber);


						synchronized (AppConfig.colorLock) {

							if (bitcakeManager instanceof LaiYangBitcakeManager && clientMessage.isWhite()) {
								LaiYangBitcakeManager lyBitcakeManager = (LaiYangBitcakeManager)bitcakeManager;
								//Zabelezimo transakciju u istoriju od poslednjeg sendera(ne originalnog!)
								ServentInfo lastSenderInfo = clientMessage.getRoute().size() == 0 ?
										clientMessage.getOriginalSenderInfo() :
										clientMessage.getRoute().get(clientMessage.getRoute().size()-1);
								lyBitcakeManager.recordGetTransaction(lastSenderInfo.getId(), amountNumber);
							}

						}


						//rebroadcast-uj svim komsijama
						for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
							//Same message, different receiver, and add us to the route table.
							MessageUtil.sendMessage(clientMessage.changeReceiver(neighbor).makeMeASender());
						}

					} else {
						//We already got this from somewhere else. /ignore
						AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
					}
				}
			} else{
				//dodaj bitcake-ove i zabelezi u istoriju dobijenih transakcija
				String amountString = clientMessage.getMessageText();

				int amountNumber = 0;
				try {
					amountNumber = Integer.parseInt(amountString);
				} catch (NumberFormatException e) {
					AppConfig.timestampedErrorPrint("Couldn't parse amount: " + amountString);
					return;
				}
				bitcakeManager.addSomeBitcakes(amountNumber);


				synchronized (AppConfig.colorLock) {
					if (bitcakeManager instanceof LaiYangBitcakeManager && clientMessage.isWhite()) {
						LaiYangBitcakeManager lyBitcakeManager = (LaiYangBitcakeManager)bitcakeManager;

						lyBitcakeManager.recordGetTransaction(clientMessage.getOriginalSenderInfo().getId(), amountNumber);
					}
				}
			}

		} else {
			AppConfig.timestampedErrorPrint("Transaction handler got: " + clientMessage);
		}
	}

}
