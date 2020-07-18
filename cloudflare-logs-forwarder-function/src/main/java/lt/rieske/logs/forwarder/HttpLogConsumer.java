package lt.rieske.logs.forwarder;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.io.UncheckedIOException;

class HttpLogConsumer implements CloseableLogConsumer {

    private final String endpoint;
    private final String credentials;
    private final CloseableHttpClient httpClient;

    HttpLogConsumer(String endpoint, String credentials) {
        this.endpoint = endpoint;
        this.credentials = "Bearer " + credentials;
        this.httpClient = HttpClients.createDefault();
    }

    @Override
    public void accept(String payload) {
        var request = new HttpPost(endpoint);
        request.setEntity(new StringEntity(payload, ContentType.TEXT_PLAIN));
        request.setHeader("Authorization", credentials);
        try (var response = httpClient.execute(request)) {
            if (response.getCode() != 200) {
                throw new IOException("Error consuming logs: " + response.getReasonPhrase());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
