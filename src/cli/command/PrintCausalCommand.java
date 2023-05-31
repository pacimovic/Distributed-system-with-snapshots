package cli.command;

import app.AppConfig;
import app.CausalBroadcastShared;
import servent.message.Message;

public class PrintCausalCommand implements CLICommand{
    @Override
    public String commandName() {
        return "print_causal";
    }

    @Override
    public void execute(String args) {
        int i = 0;
        AppConfig.timestampedStandardPrint("Current causal messages:");
        for (Message message: CausalBroadcastShared.getCommitedCausalMessages()) {
            AppConfig.timestampedStandardPrint("Message " + message + ": " + message.getMessageText() +
                    " from " + message.getOriginalSenderInfo().getId());
            i++;
        }
    }
}
