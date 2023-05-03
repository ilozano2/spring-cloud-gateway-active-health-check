# Active Health Check strategies with Spring Cloud Gateway

Nowadays, services are compounded by other upstream services. This accelerates development and allows modules to be focused on specific responsibilities increasing their quality. This is one of the main things the microservices approach tries to solve.
However, jumping from one service to another could add extra latency, and this latency could be dramatically higher when the services are not responding.

If you are running microservices, you want to prevent your upstream services from being called when they are not working properly, even if they are using a circuit breaker pattern. It will also generate a penalty in the time response. For this reason, it is important to actively check your services in order to always call responding upstream services.

Last but not least, the features can be combined with a Circuit Breaker library to immediately fall back on an alternative endpoint without suffering the first miss penalty.

Goal: Routes will forward the request to the upstream services that are healthy using a load balancer strategy

![Active Health Check Diagram](active-hc-diagram.png)

TODO - Summarise different ways to add AHC using Spring Cloud Gateway

There are a couple of features in Spring Commons that can help you to achieve this goal

**Spring Cloud Load Balancer** (SLB) is a client-side load-balancer that allows to balance traffic along different upstream service endpoints. It is part of [Spring Cloud project](https://spring.io/projects/spring-cloud) and it is included in the spring-cloud-commons library ([SLB documentation](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)).

**Client-side service discovery** feature allows the client to find and communicate with services without hard-coding the hostname and port. It is also included in the spring-cloud-commons library ([SSD documentation](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#discovery-client)).

**Spring Cloud Gateway** supports both features and can be configured thanks to [LoadBalancerClientFilter](https://cloud.spring.io/spring-cloud-gateway/2.1.x/multi/multi__global_filters.html#_loadbalancerclient_filter)/[ReactiveLoadBalancerClientFilter](https://cloud.spring.io/spring-cloud-gateway/2.1.x/multi/multi__global_filters.html#reactive-loadbalancer-client-filter).

In this post, you can see different approaches to get the benefit of this global filter included out of the box. But first, let’s explore some of those features.

### Load Balancer filter

A global filter for load balancing is included in Spring Cloud and can be activated using a special URI notation: "lb://your-service-name".

```yaml
spring:
 cloud:
   gateway:
     routes:
       - id: myRoute
         uri: lb://your-service-name
         predicates:
         - Path=/service/**
```
The load balancer filter, [ReactiveLoadBalancerClientFilter](https://cloud.spring.io/spring-cloud-gateway/2.1.x/multi/multi__global_filters.html#reactive-loadbalancer-client-filter) (for reactive applications), will detect the URI and it will replace it by an available endpoint associated to "your-service-name".

### Active Health Check

By default, traffic can be routed to all the upstream services,  even if they are unhealthy.
To prevent picking a bad one, you can enable the `health-check` configuration.

```yaml
    spring:
      cloud:  
        loadbalancer:  
          configurations: health-check
```
All the endpoints will be checked periodically using the Spring Boot Actuator health endpoint automatically. You can also customize some options like `spring.cloud.loadbalancer.health-check.<your-service-name>.path` and `spring.cloud.loadbalancer.health-check.interval`.

> Note: Default Health Check configuration will check the upstream service endpoints using the `/actuator/health` endpoint by default, which requires activating Spring Actuator in your upstream service.

For more options, check https://github.com/spring-cloud/spring-cloud-commons/blob/main/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/LoadBalancerClientsProperties.java and https://github.com/spring-cloud/spring-cloud-commons/blob/main/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/LoadBalancerProperties.java

Note: There is a built-in feature in Spring Cloud Gateway that will deploy all the services available as routes. 
This post describes the opposite, so we are declaring routes that will be load balanced including active health check.

## Static approach

You can activate client load balancing statically configuring the property `spring.cloud.discovery.client.simple.instances`.
It is a map whose key is the service name (used by the lb://<service-name> URI) and the value is an array of `org.springframework.cloud.client.ServiceInstance` objects that will point to the upstream services.

Some of the benefits of static load balancing are
* Load balancing could distribute traffic between multiple instances, sharing any stress of the services and reducing the probability of crashing
* Fault tolerance

The problem is that you are setting statically the upstream services in your configuration. If you need to change the list, you need to restart your application.

Example:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - uri: lb://hello-service # Load Balancer URI handled by ReactiveLoadBalancerClientFilter
          predicates:
            - Path=/hello
    loadbalancer:
      configurations: health-check # Required for enabling SDC with Health Checks
    discovery:
      client:
        simple: # SimpleDiscoveryClient to configure statically services
          instances:
            hello-service:
              - secure: false
                port: 8090
                host: localhost
                serviceId: hello-service
                instanceId: hello-service-1
              - secure: false
                port: 8091
                host: localhost
                serviceId: hello-service
                instanceId: hello-service-2
```

### Trying out

TODO GIF?

0. Check http://localhost:8090/actuator/status is "UP"
1. Test http://localhost:8080/headers responds 200 OK
2. Turn upstream service down sending PUT request to http://localhost:8090/status/false
3. Check http://localhost:8090/actuator/status is "DOWN"
4. Test http://localhost:8080/headers responds 503 Service Unavailable
   1. Service will respond 200 OK during interval of time before Spring checks the status of the service. The interval can be modified in the property `spring.cloud.loadbalancer.health-check.interval`

## Eureka integration (+complex, dynamic)

Having a static configuration is not so flexible, and Eureka as service discovery server removes that drawback.

The counterpart is that you require a new component in your architecture increasing maintenance. This could be not an option for some clients.

```yaml
    spring:
      application:
        name: scg-client-with-eureka
      cloud:
        loadbalancer:
          configurations: health-check # Note: required for enabling SDC with Health Checks - remove this line if you want to reproduce issues because not using HealthChecks in LB
          # Note: LoadBalancerCacheProperties.ttl (or spring.cloud.loadbalancer.cache.ttl) is 35 by default - You will need to wait 35secs after an instance turns healthy
        gateway:
          httpclient:
            wiretap: true
          routes:
            - uri: lb://hello-service
              predicates:
                - Path=/headers
              filters:
                - StripPrefix=0
    
    eureka:
      client:
        webclient:
          enabled: true
        serviceUrl:
          defaultZone: http://localhost:8761/eureka
        fetchRegistry: true
        registerWithEureka: false
      instance:
        preferIpAddress: true
```

### Trying out

TODO


## Custom Filter at Route level (dynamic)

TODO

As you know, Spring Cloud Gateway brings the option for creating your own custom filters.
Also, applying filters and changing routes without restarting your gateway is possible.

In this section, you can see a custom filter implementation for having load balancing and health checks of your services via Spring Cloud Gateway route configuration.

If you already have a service discovery server in your project, maybe this is not the best option for you. Nevertheless, this is a simple and cheap way to get two great features in your project.

```yaml
    spring:
      application:
        name: custom-service-disc
      cloud:
        loadbalancer:
          configurations: health-check # Note: required for enabling SDC with Health Checks - remove this line if you want to reproduce issues because not using HealthChecks in LB
          # Note: LoadBalancerCacheProperties.ttl (or spring.cloud.loadbalancer.cache.ttl) is 35 by default - You will need to wait 35secs after an instance turns healthy
        gateway:
          routes:
            - uri: lb://hello-service
              id: load-balanced
              predicates:
                - Path=/load-balanced/**
              filters:
                - StripPrefix=1
                - LoadBalancer=localhost:8090;localhost:8091;localhost:8092
```
The new  `LoadBalancer` route filter allows you to configure the upstream service endpoints associated with the `lb://hello-service` load balancer URI.

```
@Component
public class LoadBalancerGatewayFilterFactory extends AbstractGatewayFilterFactory<LoadBalancerGatewayFilterFactory.MyConfiguration> {

	// ...

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

```

As you can see, if a route matches the pattern `lb://<service-host>`  the `LoadBalancerGatewayFilterFactory` will associate all the upstream service endpoints coming from the filter configuration to the `service-host`.

Under the hood, a new  `ReactiveCustomDiscoveryClient` discovery client implementation has been included in order to manage upstream service endpoints in our code. 
Spring detects such bean, and, it will prioritize it in the list of [DiscoveryClient](https://github.com/spring-cloud/spring-cloud-commons/blob/main/spring-cloud-commons/src/main/java/org/springframework/cloud/client/discovery/DiscoveryClient.java) used to determine available endpoints.

### Trying out

TODO Running the example, show output
TODO Service URLs
* GET localhost:8090/actuator/health
* GET localhost:8090/hello
* PUT localhost:8090/status/{true|false}

### Next steps

In this post you have seen multiple ways to get load balancing and active health checks in your projects.

From the static approach for basic projects or proof of concepts where the number of upstream services doesn’t change. And a more dynamic approach, using Eureka or Spring Cloud Gateway filters.

To sum up, you have also seen that Spring Cloud Gateway approach is a great option if you don’t need to add an extra component to your architecture.