package com.test.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.test.example.model.MemberInfo;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.Objects;

@EnableWebFlux
@SpringBootApplication
@EnableConfigurationProperties(WiremockJupiterExtensionApplication.ServiceHttpConfig.class)
public class WiremockJupiterExtensionApplication {

	private static final String X_EXTRA_HEADER = "X-EXTRA-ID";
	private static final String APPLICATION_JSON_UTF8_VALUE = "application/json;charset=UTF-8";

	public static void main(String[] args) {
		SpringApplication.run(WiremockJupiterExtensionApplication.class, args);
	}

	@Getter
	@Setter
	@ConfigurationProperties("service.http")
	static class ServiceHttpConfig {
		private Integer connectionTimeout = 5000;
		private Integer readTimeout = 5;
		private Integer writeTimeout = 5;
		private Integer responseCacheSeconds = 3600;
	}

	@RestController
	public static class ServiceController {

		private final WebClient infoWebClient;

		public ServiceController(@Qualifier("infoWebClient") WebClient client) {
			this.infoWebClient = client;
		}

		@SneakyThrows
		@GetMapping("/v1/member/info/{memberId}")
		public Mono<MemberInfo> getMemberInfo(@PathVariable int memberId) {
			return retrieveOne(infoWebClient, memberId);
		}

		static Mono<MemberInfo> retrieveOne(WebClient webClient, int memberId) {
			return webClient
					.get()
					.uri(uriBuilder ->
							uriBuilder
									.scheme("http")
									.port(8070)
									.host("localhost")
									.path("/api/v1/member-info")
									.build()
					)
					.header("Content-Type", APPLICATION_JSON_UTF8_VALUE)
					.header(X_EXTRA_HEADER, Objects.toString(memberId))
					.retrieve()
					.bodyToMono(MemberInfo.class);
		}

		private static String getOrDefault(Object value, String nullValue) {
			return Objects.toString(value, nullValue);
		}
	}


	@Configuration
	static class TestConfig {

		@Bean
		ObjectMapper objectMapper() {
			return applyConfiguration(new ObjectMapper());
		}

		@Bean
		@Qualifier("infoWebClient")
		WebClient infoWebClient(
				WebClient.Builder builder,
				ServiceHttpConfig properties,
				ObjectMapper objectMapper
		) {
			return createWebClient(builder, properties, objectMapper);
		}

		private static WebClient createWebClient(
				WebClient.Builder builder,
				ServiceHttpConfig properties,
				ObjectMapper objectMapper
		) {
			HttpClient httpClient = HttpClient.create()
					.tcpConfiguration(client ->
							client
									.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectionTimeout())
									.doOnConnected(conn -> conn
											.addHandlerLast(new ReadTimeoutHandler(properties.getReadTimeout()))
											.addHandlerLast(new WriteTimeoutHandler(properties.getWriteTimeout()))));

			ExchangeStrategies strategies = ExchangeStrategies.builder()
					.codecs(configurer -> {
						configurer
								.defaultCodecs()
								.jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_UTF8));
						configurer
								.defaultCodecs()
								.jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_UTF8));
					}).build();

			DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory();
			factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);

			return builder
					.clientConnector(new ReactorClientHttpConnector(httpClient))
					.baseUrl("{host}/v1/")
					.uriBuilderFactory(factory)
					.exchangeStrategies(strategies)
					.defaultHeaders(headers -> {
						headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
						headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
					}).build();
		}

		static ObjectMapper applyConfiguration(ObjectMapper objectMapper) {
			return objectMapper
					.registerModule(new Jdk8Module()) .registerModule(new JavaTimeModule())
					.setSerializationInclusion(JsonInclude.Include.NON_NULL)
					.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
					.registerModules(new SimpleModule() {

						@Override
						public void setupModule(SetupContext context) {
							super.setupModule(context);
							context.insertAnnotationIntrospector(new JacksonAnnotationIntrospector() {

								@Override
								public JsonPOJOBuilder.Value findPOJOBuilderConfig(AnnotatedClass ac) {
									if (ac.hasAnnotation(JsonPOJOBuilder.class)) {
										return super.findPOJOBuilderConfig(ac);
									}
									return new JsonPOJOBuilder.Value("build", "");
								}

							});
						}

					});
		}

	}

}
