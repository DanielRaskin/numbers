package numbers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Tests
 */
public class NumbersTest {
    private static final int TEST_SERVER_PORT = 8181;
    private static final URI SERVER_URI = URI.create("http://localhost:" + TEST_SERVER_PORT);

    private static RestAPI server;
    private HttpClient httpClient = HttpClient.newBuilder().build();
    private HttpRequest getRequest = HttpRequest.newBuilder().GET().uri(SERVER_URI).build();

    // do POST request
    private int post(String data) {
        try {
            return httpClient.send(
                    HttpRequest.newBuilder().POST(
                            (data == null) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(data))
                            .uri(SERVER_URI).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // do GET request
    private HttpResponse<String> get() {
        try {
            return httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // do GET request and check result
    private void checkGetResult(String correctResult) {
        var result = get();
        assert result.statusCode() == 200;
        assert result.body().equals(correctResult);
    }

    @Before
    public void startServer() {
        // start server before each test
        server = new RestAPI();
        server.start(TEST_SERVER_PORT);
    }

    @After
    public void stopServer() {
        // stop server after each test
        server.stop();
    }

    @Test
    public void testNoData() {
        // check server with incorrect data
        checkGetResult("no data");
        assert post(null) == 400;
        assert post("") == 400;
        assert post("lkcdhvlkdhvklhnfrakj;ghnaerf;bn") == 400;
        assert post("\n\n\n") == 400;
        checkGetResult("no data");
        assert post("35c;eljfewfj;le") == 400;
        checkGetResult("no data");
    }

    @Test
    public void simpleTest() {
        // check server with different data
        checkGetResult("no data");
        assert post("55") == 200;
        checkGetResult("minimum = 55\nmaximum = 55\naverage = 55.0000");
        assert post("abc") == 400;
        assert post("") == 400;
        assert post(null) == 400;
        assert post("jhfhhfhjf") == 400;
        assert post("29.1234") == 200;
        assert post("-15.1234") == 200;
        checkGetResult("minimum = -15.1234\nmaximum = 55\naverage = 23.0000");
        assert post("0") == 200;
        assert post ("25.0000") == 200;
        checkGetResult("minimum = -15.1234\nmaximum = 55\naverage = 18.8000");
    }

    @Test
    public void concurrentTest() throws InterruptedException {
        // post data to server from different threads
        var calls = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 100; i++) {
            var data = "    " + i;
            calls.add(() -> post(data));
        }
        var postResults = Executors.newFixedThreadPool(10).invokeAll(calls);
        // check all responses to POST requests have HTTP status 200 (OK)
        assert postResults.stream().allMatch(status -> {
            try {
                return status.get() == 200;
            } catch (Exception e) {
                return false;
            }
        });
        // check values are correct
        checkGetResult("minimum = 0\nmaximum = 99\naverage = 49.5000");
    }
}
