package io.pivotal.lab.customservicedisc.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.http.client.HttpClient;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.cloud.gateway.config.HttpClientSslConfigurer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;


public class ServiceInstanceListSupplierConfiguration {

	@Bean
	public ServiceInstanceListSupplier healthCheckDiscoveryClientServiceInstanceListSupplier(
			HttpClient client,
			WebClient.Builder webClientBuilder,
			ConfigurableApplicationContext context) {
		var withSsl = webClientBuilder
				.clone()
				.clientConnector(new ReactorClientHttpConnector(client));

		return ServiceInstanceListSupplier.builder()
										  .withDiscoveryClient()
										  .withHealthChecks(withSsl.build())
										  .build(context);
	}
}