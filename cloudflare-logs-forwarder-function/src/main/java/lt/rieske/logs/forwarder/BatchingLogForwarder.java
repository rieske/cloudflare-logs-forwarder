package lt.rieske.logs.forwarder;

import java.util.function.Consumer;

class BatchingLogForwarder implements LogForwarder {

    private final int batchSize;
    private final Consumer<String> logConsumer;
    private final StringBuilder batch = new StringBuilder();

    private int logLinesInCurrentBatch = 0;

    BatchingLogForwarder(int batchSize, Consumer<String> logConsumer) {
        this.batchSize = batchSize;
        this.logConsumer = logConsumer;
    }

    @Override
    public void flush() {
        if (logLinesInCurrentBatch > 0) {
            logConsumer.accept(batch.toString());
            batch.setLength(0);
            logLinesInCurrentBatch = 0;
        }
    }

    @Override
    public void accept(String log) {
        batch.append(log).append("\n");
        logLinesInCurrentBatch++;
        if (logLinesInCurrentBatch == batchSize) {
            flush();
        }
    }
}
