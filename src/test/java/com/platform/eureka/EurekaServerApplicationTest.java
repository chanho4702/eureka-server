package com.platform.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** standalone 유레카 서버가 뜨고 대시보드(/)가 응답하는지 — 레지스트리로서의 최소 계약. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerApplicationTest {

    @LocalServerPort
    int port;

    @Test
    void dashboardIsUp() throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
    }
}
