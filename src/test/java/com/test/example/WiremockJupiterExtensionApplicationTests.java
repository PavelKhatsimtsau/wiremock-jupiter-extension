package com.test.example;

import com.github.sparkmuse.wiremock.Wiremock;
import com.github.sparkmuse.wiremock.WiremockExtension;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;
import java.util.stream.Stream;

@ActiveProfiles("test")
@WebFluxTest(controllers = WiremockJupiterExtensionApplication.ServiceController.class)
@ImportAutoConfiguration(WebClientAutoConfiguration.class)
@AutoConfigureWebTestClient
@ExtendWith(WiremockExtension.class)
class WiremockJupiterExtensionApplicationTests {

	private static final String X_EXTRA_HEADER = "X-EXTRA-ID";
	private static final String APPLICATION_JSON_UTF8_VALUE = "application/json;charset=UTF-8";

	@Wiremock(port = 8070)
	private static WireMockServer wireMockServer;

	@Autowired
	private WebTestClient webClient;

	private static Stream<Arguments> getMemberIds() {
		return Stream.of(1, 2, 3)
				.map(Objects::toString)
				.map(it -> it.repeat(4))
				.map(Arguments::of);
	}

	@ParameterizedTest
	@MethodSource("getMemberIds")
	void contextLoads(String memberId) {

		WebTestClient.ResponseSpec responseFromApi = webClient
				.get()
				.uri("/v1/member/info/" + memberId)
				.header("Content-Type", APPLICATION_JSON_UTF8_VALUE)
				.header(X_EXTRA_HEADER, Objects.toString(memberId))
				.exchange();

		responseFromApi.expectStatus()
				.isOk()
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.memberId").isEqualTo(memberId);

	}

	@AfterEach
	void halt() {
		wireMockServer.resetAll();
	}

}
