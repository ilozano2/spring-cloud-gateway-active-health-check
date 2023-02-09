package io.pivotal.lab.customservicedisc.discovery;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.stereotype.Component;

@Component
public class ReactiveCustomDiscoveryClient implements ReactiveDiscoveryClient {
	final Map<String, List<ServiceInstance>> instances = new HashMap<>();
	final LoadBalancerProperties loadBalancerProperties;

	public ReactiveCustomDiscoveryClient(LoadBalancerProperties loadBalancerProperties) {
		this.loadBalancerProperties = loadBalancerProperties;
	}


	@Override
	public String description() {
		return "Custom DC";
	}

	@Override
	public Flux<ServiceInstance> getInstances(String serviceId) {
		return Flux.fromIterable(
				Optional.ofNullable(instances.get(serviceId)).orElse(Collections.emptyList())
		);
	}

	@Override
	public Flux<String> getServices() {
		return Flux.fromIterable(instances.keySet());
	}

	@PostConstruct
	public void initialize() {
		// Just for testing without LoadBalancer filter
		// addInstance(new DefaultServiceInstance("hello-service-1", "hello-service", "localhost", 8090, false));
	}

	public synchronized void addInstance(ServiceInstance serviceInstance) {
		List<ServiceInstance> listOfInstances = instances.get(serviceInstance.getServiceId());
		if (listOfInstances == null) {
			listOfInstances = new ArrayList<>();
			instances.put(serviceInstance.getServiceId(), listOfInstances);
		}

		if (listOfInstances.stream().noneMatch(s -> s.getInstanceId().equals(serviceInstance.getInstanceId()))) {
			listOfInstances.add(
					serviceInstance
			);
		}
	}
}
