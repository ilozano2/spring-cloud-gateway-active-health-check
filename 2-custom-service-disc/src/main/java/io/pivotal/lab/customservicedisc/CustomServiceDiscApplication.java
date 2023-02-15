package io.pivotal.lab.customservicedisc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.pivotal.lab.customservicedisc.loadbalancer.ServiceInstanceListSupplierConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(defaultConfiguration = ServiceInstanceListSupplierConfiguration.class)
public class CustomServiceDiscApplication {
	public static void main(String[] args) {
		SpringApplication.run(CustomServiceDiscApplication.class, args);
	}
}
