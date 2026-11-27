package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Alert;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Income;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.Operation;
import io.github.katehok.finance.core.User;
import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletService;
import java.time.LocalDate;
import java.util.List;

public abstract class OperationCommand implements Command {

    protected final WalletService wallets;

    protected OperationCommand(WalletService wallets) {
        this.wallets = wallets;
    }

    protected abstract List<Alert> add(User user, Money amount, String category, LocalDate date, String note);

    public static String typeOf(Operation op) {
        return op instanceof Income ? "income" : "expense";
    }

    @Override
    public String usage() {
        return name() + " <amount> <category> [yyyy-mm-dd] [note]";
    }

    @Override
    public String hint(Reason reason) {
        if (reason == Reason.UNKNOWN_CATEGORY) return CREATE_CATEGORY_HINT;
        return null;
    }

    @Override
    public void execute(Session session, String[] args) {
        if (args.length < 3) {
            printUsage(session);
            return;
        }
        Money amount = Args.tryAmount(args[1]);
        if (amount == null) {
            session.println(Args.BAD_AMOUNT);
            return;
        }
        LocalDate date = null;
        int noteStart = 3;
        if (args.length > 3 && Args.isDateLike(args[3])) {
            date = Args.tryDate(args[3]);
            if (date == null) {
                session.println(Args.BAD_DATE);
                return;
            }
            noteStart = 4;
        }
        List<Alert> fresh = add(session.getUser(), amount, args[2], date, Args.join(args, noteStart));
        Wallet wallet = session.getWallet();
        Operation op = wallet.getOperations().getLast();
        session.println("Added " + typeOf(op) + " " + op.amount() + " to " + op.category() + " on " + op.date()
                + ". Balance: " + wallet.getBalance());
        session.printAlerts(fresh);
    }
}
