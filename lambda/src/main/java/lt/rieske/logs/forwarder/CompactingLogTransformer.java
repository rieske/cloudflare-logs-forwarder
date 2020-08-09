package lt.rieske.logs.forwarder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

class CompactingLogTransformer implements Function<String, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String SEPARATOR = " ";

    @Override
    public String apply(String rawLogLine) {
        return LogLine.fromJson(rawLogLine).toString();
    }

    private static class LogLine {
        private final String clientRequestMethod;
        private final String clientRequestHost;
        private final String clientRequestUri;
        private final String clientIp;
        private final String clientCountry;
        private final String edgeResponseStatus;
        private final String edgeResponseBytes;
        private final String cacheStatus;
        private final String rayId;
        private final String edgeStartTimestamp;
        private final String edgeEndTimestamp;
        private final String clientRequestUserAgent;

        @JsonCreator
        LogLine(@JsonProperty("ClientRequestMethod") String clientRequestMethod,
                @JsonProperty("ClientRequestHost") String clientRequestHost,
                @JsonProperty("ClientRequestURI") String clientRequestUri,
                @JsonProperty("ClientIP") String clientIp,
                @JsonProperty("ClientCountry") String clientCountry,
                @JsonProperty("EdgeResponseStatus") String edgeResponseStatus,
                @JsonProperty("EdgeResponseBytes") String edgeResponseBytes,
                @JsonProperty("CacheCacheStatus") String cacheStatus,
                @JsonProperty("RayID") String rayId,
                @JsonProperty("EdgeStartTimestamp") String edgeStartTimestamp,
                @JsonProperty("EdgeEndTimestamp") String edgeEndTimestamp,
                @JsonProperty("ClientRequestUserAgent") String clientRequestUserAgent) {
            this.clientRequestMethod = clientRequestMethod;
            this.clientRequestHost = clientRequestHost;
            this.clientRequestUri = clientRequestUri;
            this.clientIp = clientIp;
            this.clientCountry = clientCountry;
            this.edgeResponseStatus = edgeResponseStatus;
            this.edgeResponseBytes = edgeResponseBytes;
            this.cacheStatus = cacheStatus;
            this.rayId = rayId;
            this.edgeStartTimestamp = edgeStartTimestamp;
            this.edgeEndTimestamp = edgeEndTimestamp;
            this.clientRequestUserAgent = clientRequestUserAgent;
        }

        static LogLine fromJson(String json) {
            try {
                return OBJECT_MAPPER.readValue(json, LogLine.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String toString() {
            return clientRequestMethod + SEPARATOR +
                    clientRequestHost + SEPARATOR +
                    clientRequestUri + SEPARATOR +
                    clientIp + SEPARATOR +
                    clientCountry + SEPARATOR +
                    edgeResponseStatus + SEPARATOR +
                    edgeResponseBytes + SEPARATOR +
                    cacheStatus + SEPARATOR +
                    rayId + SEPARATOR +
                    edgeStartTimestamp + SEPARATOR +
                    edgeEndTimestamp + SEPARATOR +
                    clientRequestUserAgent;
        }
    }
}
