package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Alert;
import io.github.katehok.finance.core.WalletService;
import java.util.List;

public class AlertsCommand implements Command {

    private final WalletService wallets;

    public AlertsCommand(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public String name() {
        return "alerts";
    }

    @Override
    public String usage() {
        return "alerts";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "show budget and balance alerts"));
    }

    @Override
    public void execute(Session session, String[] args) {
        List<Alert> alerts = wallets.alerts(session.getUser());
        if (alerts.isEmpty()) {
            session.println("No alerts. All good");
            return;
        }
        session.printAlerts(alerts);
    }
}
