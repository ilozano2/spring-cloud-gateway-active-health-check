package io.pivotal.lab.customservicedisc.filter;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import io.pivotal.lab.customservicedisc.discovery.ReactiveCustomDiscoveryClient;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class LoadBalancerGatewayFilterFactory extends AbstractGatewayFilterFactory<LoadBalancerGatewayFilterFactory.MyConfiguration> {

	private final ReactiveCustomDiscoveryClient discoveryClient;
	private final RouteDefinitionLocator routeDefinitionLocator;

	public LoadBalancerGatewayFilterFactory(ReactiveCustomDiscoveryClient discoveryClient, RouteDefinitionLocator routeDefinitionLocator) {
		super(MyConfiguration.class);
		this.discoveryClient = discoveryClient;
		this.routeDefinitionLocator = routeDefinitionLocator;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return List.of("instances");
	}

	@Override
	public GatewayFilter apply(MyConfiguration config) {
		return (exchange, chain) -> {
			final Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
			if (StringUtils.hasText(config.getInstances()) && route.getUri().getScheme().equals("lb")) {
				config.getServiceInstances(route.getUri().getHost()).forEach(discoveryClient::addInstance);
			}

			return chain.filter(exchange);
		};
	}

	public static class MyConfiguration {
		private String instances;
		private String routeId;

		public String getInstances() {
			return instances;
		}

		public void setInstances(String instances) {
			this.instances = instances;
		}

		/**
		 * Convenient method for getting DefaultServiceInstance items.
		 * @return
		 */
		public List<DefaultServiceInstance> getServiceInstances(String serviceName) {
			AtomicInteger counter = new AtomicInteger();
			return Arrays.stream(instances.split(";"))
					.map(uri -> uri.split(":"))
					.map(uriAndPort -> new DefaultServiceInstance(serviceName + "-" + counter.incrementAndGet(), serviceName, uriAndPort[0], Integer.valueOf(uriAndPort[1]), false))
					.collect(Collectors.toList());
		}
	}
}
