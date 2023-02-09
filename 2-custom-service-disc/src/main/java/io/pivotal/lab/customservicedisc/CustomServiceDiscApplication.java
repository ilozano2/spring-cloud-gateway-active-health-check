package io.pivotal.lab.customservicedisc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import io.pivotal.lab.customservicedisc.filter.LoadBalancerGatewayFilterFactory;

@SpringBootApplication
public class CustomServiceDiscApplication {

	/**
	 * TODO Important: This way of adding routes is needed in order to check Circuit Breaker
	 */
	@Bean
	public RouteLocator circuitBreakerRouteLocator(RouteLocatorBuilder builder, LoadBalancerGatewayFilterFactory loadBalancerGatewayFilterFactory) {
		var lbConfig = new LoadBalancerGatewayFilterFactory.MyConfiguration();
		lbConfig.setInstances("localhost:8090;localhost:8091;localhost:8092");
		return builder.routes()
					  .route("just-cb",
							  r -> r
									  .path("/just-cb/**")
									  .filters(f -> f
											  .filter(loadBalancerGatewayFilterFactory.apply(lbConfig), Ordered.HIGHEST_PRECEDENCE)
											  .stripPrefix(1)
											  .circuitBreaker(config -> config.setFallbackUri("forward:/fallback/get"))
									  )
									.uri("lb://my-lb"))
					  .build();
	}

	public static void main(String[] args) {
		SpringApplication.run(CustomServiceDiscApplication.class, args);
	}
}
