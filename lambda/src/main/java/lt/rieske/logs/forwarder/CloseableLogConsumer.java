package lt.rieske.logs.forwarder;

import java.io.Closeable;
import java.util.function.Consumer;

interface CloseableLogConsumer extends Consumer<String>, Closeable {
}
