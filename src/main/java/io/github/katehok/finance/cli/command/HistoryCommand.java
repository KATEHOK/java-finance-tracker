package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.cli.Table;
import io.github.katehok.finance.cli.Table.Align;
import io.github.katehok.finance.core.Operation;
import java.util.List;

public class HistoryCommand implements Command {

    @Override
    public String name() {
        return "history";
    }

    @Override
    public String usage() {
        return "history [n]";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "show the last operations, e.g. history 10"));
    }

    @Override
    public void execute(Session session, String[] args) {
        if (args.length > 2) {
            printUsage(session);
            return;
        }
        List<Operation> ops = session.getWallet().getOperations();
        int count = ops.size();
        if (args.length == 2) {
            Integer num = Args.tryNum(args[1]);
            if (num == null || num < 1) {
                session.println("Oops: Number of operations must be a whole number like 10");
                return;
            }
            count = Math.min(num, count);
        }
        if (ops.isEmpty()) {
            session.println("No operations yet. Add one: income <amount> <category>");
            return;
        }
        Table table = new Table("", Align.LEFT, Align.LEFT, Align.LEFT, Align.RIGHT, Align.LEFT);
        table.addRow("Date", "Type", "Category", "Amount", "Note");
        for (Operation op : ops.subList(ops.size() - count, ops.size())) {
            table.addRow(op.date().toString(), OperationCommand.typeOf(op), op.category(), op.amount().toString(),
                    op.note());
        }
        session.print(table.format());
    }
}
