package lt.rieske.logs.forwarder;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;


public class S3EventHandler implements RequestHandler<S3Event, Integer> {

    private static final LambdaLogger logger = LambdaRuntime.getLogger();

    private final S3Client s3;
    private final Function<String, String> logTransformer;
    private final Consumer<String> logForwarder;
    private final Closeable flusher;

    public S3EventHandler() {
        this(S3Client.builder().build(), getRequiredEnvVar("LogForwarderHttpEndpoint"),
                getRequiredEnvVar("LogForwarderCredentials"), 1000);
    }

    S3EventHandler(S3Client s3, String logConsumerEndpoint, String logConsumerCredentials, int batchSize) {
        this(s3, new CompactingLogTransformer(), new HttpLogConsumer(logConsumerEndpoint, logConsumerCredentials), batchSize);
    }

    private S3EventHandler(S3Client s3, Function<String, String> logTransformer, CloseableLogConsumer logConsumer, int batchSize) {
        this(s3, logTransformer, new BatchingLogForwarder(batchSize, logConsumer), logConsumer);
    }

    private S3EventHandler(S3Client s3, Function<String, String> logTransformer, LogForwarder logForwarder, CloseableLogConsumer consumerFlusher) {
        this(s3, logTransformer, logForwarder, () -> {
            logForwarder.flush();
            consumerFlusher.close();
        });
    }

    S3EventHandler(S3Client s3, Function<String, String> logTransformer, Consumer<String> logForwarder, Closeable flusher) {
        this.s3 = s3;
        this.logTransformer = logTransformer;
        this.logForwarder = logForwarder;
        this.flusher = flusher;
    }

    @Override
    public Integer handleRequest(S3Event event, Context context) {
        logger.log("Handling S3 event: " + event);

        event.getRecords().stream().map(record -> downloadS3Object(record.getS3())).forEach(this::processLogFile);

        try {
            flusher.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return 0;
    }

    private void processLogFile(InputStream s3Stream) {
        try (var gzip = new GZIPInputStream(s3Stream);
             var reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            reader.lines().map(logTransformer).forEach(logForwarder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InputStream downloadS3Object(S3EventNotification.S3Entity s3Entity) {
        String key = s3Entity.getObject().getKey();
        String bucket = s3Entity.getBucket().getName();

        logger.log("Downloading " + key + " from S3 bucket " + bucket);
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private static String getRequiredEnvVar(String varName) {
        var value = System.getenv(varName);
        if (value == null) {
            throw new IllegalStateException(varName + " has to be configured");
        }
        return value;
    }
}
