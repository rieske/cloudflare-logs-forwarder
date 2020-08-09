package lt.rieske.logs.forwarder;

import java.util.function.Consumer;

interface LogForwarder extends Consumer<String> {
    void flush();
}
