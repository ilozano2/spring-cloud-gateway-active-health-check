package io.spring.ilozano.heatlhcheck.active.service.controller;

import java.util.List;
import java.util.Map;

import io.spring.ilozano.heatlhcheck.active.service.indicator.SwitchHealhCheckIndicator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple controller for testing.
 */
@RestController
public class ServiceController {

	@Autowired
	private SwitchHealhCheckIndicator switchIndicator;
	@Autowired
	private DiscoveryClient discoveryClient;

	@Value("${server.port}")
	Integer port;

	/**
	 * Hello world endpoint.
	 */
	@GetMapping("/hello")
	public HelloDto hello() {
		return new HelloDto("hello world from port %s!".formatted(port));
	}

	/**
	 * It turns the service's health up/down on-demand.
	 */
	@PutMapping("/status/{isUp}")
	public void putStatus(@PathVariable("isUp") boolean isUp) {
		switchIndicator.setDown(!isUp);
	}

	/**
	 * It returns the {@link ServiceInstance}s available for a specific {applicationName}.
	 */
	@RequestMapping("/service-instances/{applicationName}")
	public List<ServiceInstance> serviceInstancesByApplicationName(
			@PathVariable String applicationName) {
		return this.discoveryClient.getInstances(applicationName);
	}

	/**
	 * It returns the service names available.
	 */
	@RequestMapping("/service-instances")
	public List<String> services() {
		return this.discoveryClient.getServices();
	}

	public record HelloDto(String message) {}
}
