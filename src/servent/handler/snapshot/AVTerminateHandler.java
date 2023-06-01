package servent.handler.snapshot;

import app.snapshot_bitcake.BitcakeManager;
import servent.handler.MessageHandler;
import servent.message.Message;

public class AVTerminateHandler implements MessageHandler {

    private Message clientMessage;
    private BitcakeManager bitcakeManager;
    private boolean doRebroadcast = false;

    public AVTerminateHandler(Message clientMessage, BitcakeManager bitcakeManager, boolean doRebroadcast) {
        this.clientMessage = clientMessage;
        this.bitcakeManager = bitcakeManager;
        this.doRebroadcast = doRebroadcast;
    }

    @Override
    public void run() {
        /*
        Prestajemo sa snimanjem kanala
        Ispisujemo svoje stanje
         */
    }
}
