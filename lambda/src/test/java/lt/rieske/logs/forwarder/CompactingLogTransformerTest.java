package lt.rieske.logs.forwarder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompactingLogTransformerTest {

    private final CompactingLogTransformer transformer = new CompactingLogTransformer();

    @Test
    void deserializesLogLine() {
        var logLine = transformer.apply("{" +
                "\"CacheCacheStatus\":\"hit\"," +
                "\"ClientIP\":\"127.0.0.1\"," +
                "\"ClientCountry\":\"LT\"," +
                "\"ClientRequestHost\":" + "\"foo.bar\"," +
                "\"ClientRequestMethod\":\"GET\"," +
                "\"ClientRequestURI\":\"/foo/bar\"," +
                "\"ClientRequestUserAgent\":\"curl foo bar\"," +
                "\"EdgeStartTimestamp\":1," +
                "\"EdgeEndTimestamp\":2," +
                "\"EdgeResponseBytes\":42," +
                "\"EdgeResponseStatus\":200," +
                "\"RayID\":\"foobar\"" +
                "}");

        assertThat(logLine).isEqualTo("GET foo.bar /foo/bar 127.0.0.1 LT 200 42 hit foobar 1 2 curl foo bar");
    }

    @Test
    void deserializesLogLineWithExtraFields() {
        var logLine = transformer.apply("{" +
                "\"CacheCacheStatus\":\"hit\"," +
                "\"ClientIP\":\"127.0.0.1\"," +
                "\"ClientCountry\":\"LT\"," +
                "\"ClientRequestHost\":" + "\"foo.bar\"," +
                "\"ClientRequestMethod\":\"GET\"," +
                "\"ClientRequestURI\":\"/foo/bar\"," +
                "\"ClientRequestUserAgent\":\"curl foo bar\"," +
                "\"EdgeStartTimestamp\":1," +
                "\"EdgeEndTimestamp\":2," +
                "\"EdgeResponseBytes\":42," +
                "\"EdgeResponseStatus\":200," +
                "\"foo\":200," +
                "\"bar\":\"fizzbuzz\"," +
                "\"someObject\":{}," +
                "\"someArray\":[\"foo\",\"bar\"]," +
                "\"RayID\":\"foobar\"" +
                "}");

        assertThat(logLine).isEqualTo("GET foo.bar /foo/bar 127.0.0.1 LT 200 42 hit foobar 1 2 curl foo bar");
    }

    @Test
    void deserializesEmptyLogLine() {
        var logLine = transformer.apply("{}");

        assertThat(logLine).isEqualTo("null null null null null null null null null null null null");
    }
}
