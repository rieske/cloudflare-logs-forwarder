package lt.rieske.logs.forwarder;

import java.util.function.Consumer;

class BatchingLogForwarder implements LogForwarder {

    private final int batchSize;
    private final Consumer<String> logConsumer;
    private final StringBuilder batch = new StringBuilder();

    private int currentBatchSize = 0;

    BatchingLogForwarder(int batchSize, Consumer<String> logConsumer) {
        this.batchSize = batchSize;
        this.logConsumer = logConsumer;
    }

    @Override
    public void flush() {
        if (currentBatchSize > 0) {
            logConsumer.accept(batch.toString());
            batch.setLength(0);
            currentBatchSize = 0;
        }
    }

    @Override
    public void accept(String log) {
        batch.append(log).append("\n");
        currentBatchSize++;
        if (currentBatchSize == batchSize) {
            flush();
        }
    }
}
