package lt.rieske.logs.forwarder;

import com.adobe.testing.s3mock.junit5.S3MockExtension;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.RequestListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(S3MockExtension.class)
class S3EventHandlerTest {

    private static final String SMALL_LOG = "logs.json.gz";
    private static final String LARGE_LOG = "log-large.json.gz";

    private static final Path RESOURCES_DIR = Path.of("src/test/resources");

    @Test
    void consumesAllLogLinesFromS3(S3Client s3) {
        List<String> logs = new ArrayList<>();
        var eventHandler = new S3EventHandler(s3, Function.identity(), logs::add, () -> {
        });

        eventHandler.handleRequest(logsUploadedEvent(s3, SMALL_LOG), null);

        assertThat(logs).containsExactly(
                "{\"CacheCacheStatus\":\"cache1\",\"CacheResponseBytes\":1,\"CacheResponseStatus\":1,\"ClientCountry\":\"LT\",\"ClientIP\":\"127.0.0.1\",\"ClientIPClass\":\"clean\",\"ClientRequestHost\":\"foo.bar\",\"ClientRequestBytes\":42,\"ClientRequestMethod\":\"GET\",\"ClientRequestURI\":\"/foo/bar\",\"ClientRequestAgent\":\"curl foo bar\",\"EdgeResponseBytes\":42,\"EdgeResponseStatus\":200,\"EdgeStartTimestamp\":1,\"EdgeEndTimestamp\":2,\"RayID\":\"foobar\"}",
                "{\"CacheCacheStatus\":\"cache2\",\"CacheResponseBytes\":2,\"CacheResponseStatus\":2,\"ClientCountry\":\"PL\",\"ClientIP\":\"127.0.0.2\",\"ClientIPClass\":\"clean\",\"ClientRequestHost\":\"fizz.buzz\",\"ClientRequestBytes\":11,\"ClientRequestMethod\":\"PATCH\",\"ClientRequestURI\":\"/fizz/buzz\",\"ClientRequestAgent\":\"curl fizz buzz\",\"EdgeResponseBytes\":11,\"EdgeResponseStatus\":201,\"EdgeStartTimestamp\":2,\"EdgeEndTimestamp\":3,\"RayID\":\"fizzbuzz\"}",
                "{\"CacheCacheStatus\":\"cache3\",\"CacheResponseBytes\":2,\"CacheResponseStatus\":2,\"ClientCountry\":\"DE\",\"ClientIP\":\"127.0.0.3\",\"ClientIPClass\":\"clean\",\"ClientRequestHost\":\"banana.potato\",\"ClientRequestBytes\":11,\"ClientRequestMethod\":\"POST\",\"ClientRequestURI\":\"/banana/potato\",\"ClientRequestAgent\":\"curl banana potato\",\"EdgeResponseBytes\":11,\"EdgeResponseStatus\":201,\"EdgeStartTimestamp\":2,\"EdgeEndTimestamp\":3,\"RayID\":\"bananapotato\"}"
        );
    }

    @Test
    void transformsAndForwardsLogLinesFormS3(S3Client s3) {
        withLogConsumingHttpServer(endpoint -> {
            var eventHandler = new S3EventHandler(s3, endpoint, "credentials", 100);

            eventHandler.handleRequest(logsUploadedEvent(s3, SMALL_LOG), null);
        }).assertLogBodySent("GET foo.bar /foo/bar 127.0.0.1 LT 200 42 cache1 foobar 1 2 null\n" +
                "PATCH fizz.buzz /fizz/buzz 127.0.0.2 PL 201 11 cache2 fizzbuzz 2 3 null\n" +
                "POST banana.potato /banana/potato 127.0.0.3 DE 201 11 cache3 bananapotato 2 3 null\n");
    }

    @Test
    void transformsAndForwardsLogLinesFormS3InTwoBatches(S3Client s3) {
        withLogConsumingHttpServer(endpoint -> {
            var eventHandler = new S3EventHandler(s3, endpoint, "credentials", 2);

            eventHandler.handleRequest(logsUploadedEvent(s3, SMALL_LOG), null);
        }).assertLogBodySent("GET foo.bar /foo/bar 127.0.0.1 LT 200 42 cache1 foobar 1 2 null\n" +
                "PATCH fizz.buzz /fizz/buzz 127.0.0.2 PL 201 11 cache2 fizzbuzz 2 3 null\n"
        ).assertLogBodySent("POST banana.potato /banana/potato 127.0.0.3 DE 201 11 cache3 bananapotato 2 3 null\n");
    }

    @Test
    void transformsAndForwardsLargeLog(S3Client s3) {
        var bytesForwarded = new AtomicLong(0);
        RequestListener payloadSizeCounter = (request, response) -> bytesForwarded.addAndGet(request.getBody().length);

        withLogConsumingHttpServer(payloadSizeCounter, endpoint -> {
            var eventHandler = new S3EventHandler(s3, endpoint, "credentials", 1000);

            eventHandler.handleRequest(logsUploadedEvent(s3, LARGE_LOG), null);
        }).assertNumberOfBatchesSent(300);

        System.out.println("Megabytes forwarded: " + bytesForwarded.get()/1024/1024);
    }

    private static LogDispatchAsserter withLogConsumingHttpServer(Consumer<String> test) {
        return withLogConsumingHttpServer((request, response) -> {}, test);
    }

    private static LogDispatchAsserter withLogConsumingHttpServer(RequestListener requestListener, Consumer<String> test) {
        var server = new WireMockServer(options().dynamicPort());
        try {
            server.addMockServiceRequestListener(requestListener);
            server.start();
            server.stubFor(post(urlPathEqualTo("/")).willReturn(aResponse().withStatus(200)));
            test.accept(server.baseUrl());
        } finally {
            server.stop();
        }
        return new LogDispatchAsserter(server);
    }

    private static class LogDispatchAsserter {
        private final WireMockServer server;

        private LogDispatchAsserter(WireMockServer server) {
            this.server = server;
        }

        LogDispatchAsserter assertLogBodySent(String body) {
            server.verify(1, postRequestedFor(urlPathEqualTo("/")).withRequestBody(equalTo(body)));
            return this;
        }

        LogDispatchAsserter assertNumberOfBatchesSent(int batches) {
            server.verify(batches, postRequestedFor(urlPathEqualTo("/")));
            return this;
        }
    }

    private static S3Event logsUploadedEvent(S3Client s3, String key) {
        String bucketName = "test-cloudflare-logs";

        s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        s3.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), RESOURCES_DIR.resolve(key));

        var record = new S3EventNotification.S3EventNotificationRecord(
                null, null, null, null,
                null, null, null,
                new S3EventNotification.S3Entity(
                        null,
                        new S3EventNotification.S3BucketEntity(bucketName, null, null),
                        new S3EventNotification.S3ObjectEntity(key, null, null, null, null),
                        null
                ), null
        );
        return new S3Event(List.of(record));
    }
}
