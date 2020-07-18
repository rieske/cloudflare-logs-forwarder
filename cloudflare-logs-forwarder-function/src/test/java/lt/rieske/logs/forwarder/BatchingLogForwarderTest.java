package lt.rieske.logs.forwarder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchingLogForwarderTest {

    @Test
    void forwardsLogBatch() {
        List<String> batchConsumer = new ArrayList<>();
        var forwarder = new BatchingLogForwarder(10, batchConsumer::add);

        sendLogMessages(forwarder, 10);

        assertThat(batchConsumer).hasSize(1);
        assertThat(batchConsumer).containsExactly("0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n");
    }

    @Test
    void forwardsThreeLogBatches() {
        List<String> batchConsumer = new ArrayList<>();
        var forwarder = new BatchingLogForwarder(2, batchConsumer::add);

        sendLogMessages(forwarder, 6);

        assertThat(batchConsumer).hasSize(3);
        assertThat(batchConsumer).containsExactly("0\n1\n", "2\n3\n", "4\n5\n");
    }

    @Test
    void requiresFlushForNonFullBatch() {
        List<String> batchConsumer = new ArrayList<>();
        var forwarder = new BatchingLogForwarder(10, batchConsumer::add);

        sendLogMessages(forwarder, 9);

        assertThat(batchConsumer).isEmpty();

        forwarder.flush();

        assertThat(batchConsumer).hasSize(1);
        assertThat(batchConsumer).containsExactly("0\n1\n2\n3\n4\n5\n6\n7\n8\n");
    }

    @Test
    void requiresFlushForNonFullSecondBatch() {
        List<String> batchConsumer = new ArrayList<>();
        var forwarder = new BatchingLogForwarder(10, batchConsumer::add);

        sendLogMessages(forwarder, 11);

        assertThat(batchConsumer).hasSize(1);
        assertThat(batchConsumer).containsExactly("0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n");

        forwarder.flush();

        assertThat(batchConsumer).hasSize(2);
        assertThat(batchConsumer).containsExactly("0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n", "10\n");
    }

    @Test
    void forwardsNothingWhenNothingToFlush() {
        List<String> batchConsumer = new ArrayList<>();
        var forwarder = new BatchingLogForwarder(10, batchConsumer::add);

        forwarder.flush();

        assertThat(batchConsumer).isEmpty();
    }

    private static void sendLogMessages(LogForwarder forwarder, int upperBound) {
        for (int i = 0; i < upperBound; i++) {
            forwarder.accept(Integer.toString(i));
        }
    }
}
