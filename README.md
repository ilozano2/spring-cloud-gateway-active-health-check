# Active health check strategies with Spring Cloud Gateway

Nowadays, applications are built as a collection of small independent upstream services. This accelerates development and allows modules to be focused on specific responsibilities, increasing their quality. This is one of the main advantages of using a microservice approach. However, jumping from one service to another can add extra latency, and this latency can be dramatically higher when the services are not responding.

If you run microservices, you want to prevent your upstream services from being called when they are not working properly. Even using a circuit breaker pattern can also generate a penalty in the response time. For this reason, it is sometimes better to actively check your upstream services to verify they are ready before they are needed.

>A health check is a way to determine if a service can respond correctly according to its status, preventing timeouts and errors.
>
> **Passive health check** is done during request handling. If the service is finally unhealthy, the application will return a failure marking the endpoint unhealthy. It can add extra latency.
>
> **Active health check** will check and drop unhealthy services in the background before receiving the request. It doesn't add extra latency.

Last but not least, these features can be combined with a circuit breaker library to immediately fall back on an alternative endpoint without suffering the first miss penalty.

The goal is for routes to forward the requests to upstream services that are healthy by using a load balancer strategy:

![Active health check Diagram](active-hc-diagram.png)

This post is divided into two parts:
1. "Spring features you need" - describing which Spring’s features you need to get active health check.
2. "Registering endpoints for your services" - visiting some approaches for adding one or more endpoints to your routes.

# 1. Spring features you need

There are some features in Spring that can help you to get active health check

* **Spring Cloud Load Balancer** (SLB) is a client-side load-balancer that allows balancing traffic between different upstream service endpoints. It is part of [Spring Cloud project](https://spring.io/projects/spring-cloud), and is included in the spring-cloud-commons library (see the [SLB documentation](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)).
* The client-side service discovery feature lets the client find and communicate with services without hard-coding the hostname and port. It is also included in the spring-cloud-commons library (see the [Service Discovery documentation](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#discovery-client)).

**Spring Cloud Gateway**
[Spring Cloud Gateway](https://cloud.spring.io/spring-cloud-gateway) provides a library for building API gateways on top of Spring and Java.
It supports the above features through the [LoadBalancerClientFilter](https://cloud.spring.io/spring-cloud-gateway/2.1.x/multi/multi__global_filters.html#_loadbalancerclient_filter)/[ReactiveLoadBalancerClientFilter](https://cloud.spring.io/spring-cloud-gateway/2.1.x/multi/multi__global_filters.html#reactive-loadbalancer-client-filter) global filters.
In this post, you can see different ways to use one of those global filters.

First, though, let’s explore some of those features.

### Spring Cloud Load Balancer filter

A global filter for load balancing is included in Spring Cloud and can be activated by using a special URI notation: `lb://your-service-name`.

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

The load balancer filter, [ReactiveLoadBalancerClientFilter](https://cloud.spring.io/spring-cloud-gateway/2.1.x/multi/multi__global_filters.html#reactive-loadbalancer-client-filter) (for reactive applications), will detect the URI and replace it with an available endpoint associated with "your-service-name".

Take into account that you need to register "your-service-name" in the Service Discovery registry. We will see different ways you can do it in the following sections.

### Active health check

By default, traffic is routed to upstream services, even if they are unhealthy. 
To prevent picking a bad one, you can enable the `health-check` configuration provided by the Load Balancer Client from Spring Cloud:

```yaml
    spring:
      cloud:  
        loadbalancer:  
          configurations: health-check
```
All the endpoints will be checked periodically by automatically using the Spring Boot Actuator health endpoint. 
You can also customize some options like `spring.cloud.loadbalancer.health-check.<your-service-name>.path` and `spring.cloud.loadbalancer.health-check.interval`.

> The default health check configuration checks the upstream service endpoints by using the `/actuator/health` endpoint, which requires activating Spring Actuator in your upstream service.

For more options, explore the [LoadBalancerClientsProperties](https://github.com/spring-cloud/spring-cloud-commons/blob/main/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/LoadBalancerClientsProperties.java) and [LoadBalancerProperties](https://github.com/spring-cloud/spring-cloud-commons/blob/main/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/LoadBalancerProperties.java) classes

> There is a built-in feature in Spring Cloud Gateway that will deploy all the services available as routes. This post describes the opposite, so we are declaring routes that are load balanced, including active health check.

# 2. Registering endpoints for your services

In the previous section, you specified a load-balanced URI (`lb://your-service-name`), but now you need to register the endpoints associated with the service name of the URI. 
We are visiting some approaches in the following sections.

## Static approach

You can statically activate client load balancing by configuring the `spring.cloud.discovery.client.simple.instances` property.
It is a map whose key is the service name (used by the lb:// URI) and the value is an array of `org.springframework.cloud.client.ServiceInstance` objects that point to the upstream services.

Some benefits of static load balancing include:
* Load balancing could distribute traffic between multiple instances, sharing any stress of the services and reducing the probability of crashing.
* Fault tolerance.

The problem is that you are statically setting the upstream services in your configuration. If you need to change the list, you need to restart your application.

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
      configurations: health-check # Required for enabling SDC with health checks
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

1. Run servers
```shell
# Run server 1
SERVER_PORT=8090 ./gradlew :service:bootRun
```
```shell
# Run server 2
SERVER_PORT=8091 ./gradlew :service:bootRun
```

2. Check http://localhost:8090/actuator/health is "UP"

```shell
curl http://localhost:8090/actuator/health
```

```
 {"status":"UP"}
```

3. Test http://localhost:8080/hello responds 200 OK

```shell
curl localhost:8090/hello
```

```
{ "message": "hello world!"}%
```

4. Run Spring Cloud Gateway

```shell
./gradlew :1-service-disc-by-properties:bootRun
```

5. Test Spring Cloud Gateway balancer

```shell
curl localhost:8881/hello
```

```
{ "message": "hello world from port 8090!"}%
```

```shell
curl localhost:8881/hello
```

```
{ "message": "hello world from port 8091!"}%
```

You could need to run multiple times the previous commands to get a response from a different server.

6. Mark server 1 as unhealthy sending PUT request to http://localhost:8090/status/false

```shell
curl localhost:8090/status/false -X PUT
```

7. Check http://localhost:8090/actuator/status is "DOWN"

```shell
curl http://localhost:8090/actuator/health
```

```
{"status":"DOWN"}
```

8. Run multiple times a GET request to http://localhost:8881/hello and see that you only get response from port 8091

You could receive one response on port 8090 owing the healthcheck haven't checked the endpoint when you send the request. 
The interval can be modified in the property spring.cloud.loadbalancer.health-check.interval `spring.cloud.loadbalancer.health-check.interval`

Also, you can see some messages that describe one of the upstream endpoints as not healthy, and therefore, it is unavailable.

```
2023-05-08 14:59:53.151 DEBUG 9906 --- [ctor-http-nio-3] r.n.http.client.HttpClientOperations     : [12d42e83-77, L:/127.0.0.1:57439 - R:localhost/127.0.0.1:8090] Received response (auto-read:false) : RESPONSE(decodeResult: success, version: HTTP/1.1)
HTTP/1.1 503 Service Unavailable
```

```shell
curl localhost:8881/hello
```

```
{ "message": "hello world from port 8091!"}%
```

9. Mark server 2 as unhealthy sending PUT request to http://localhost:8091/status/false

```shell
curl localhost:8091/status/false -X PUT
```

10. Run some GET requests to http://localhost:8881/hello and see that it responds "503 Service Unavailable"

```shell
curl localhost:8881/hello
```

```
{"timestamp":"2023-05-08T13:07:48.704+00:00","path":"/hello","status":503,"error":"Service Unavailable","requestId":"6b5d6010-199"}%
```

11. Stop all the servers started in the previous steps

## Eureka integration (+complex, dynamic)

Having a static configuration is not very flexible, but using Eureka as a service discovery can remove that drawback.

The cost is that you require a new component in your architecture which can increase your maintenance burden. This might not be an option for some clients.

The following example configures Eureka integration:

```yaml
    spring:
      application:
        name: scg-client-with-eureka
      cloud:
        loadbalancer:
          configurations: health-check # Note: required for enabling SDC with health checks - remove this line if you want to reproduce issues because not using health checks in LB
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

1. Run Eureka Server
```shell
./gradlew :eureka-server:bootRun
```

Wait until you can see Eureka server was started up

```
2023-06-26 12:51:46.901  INFO 88601 --- [       Thread-9] e.s.EurekaServerInitializerConfiguration : Started Eureka Server
```

2. Run servers including `eureka` profile
```shell
# Run server 1
SPRING_PROFILES_ACTIVE=eureka SERVER_PORT=8090 ./gradlew :service:bootRun
```
```shell
# Run server 2
SPRING_PROFILES_ACTIVE=eureka SERVER_PORT=8091 ./gradlew :service:bootRun
```

You should see that the sever instances were added into Eureka in the servers' logs from step 1.

```
2023-06-26 12:52:50.805  INFO 88601 --- [nio-8761-exec-3] c.n.e.registry.AbstractInstanceRegistry  : Registered instance HELLO-SERVICE/192.168.0.14:hello-service:8090 with status UP (replication=true)
2023-06-26 12:53:29.127  INFO 88601 --- [nio-8761-exec-9] c.n.e.registry.AbstractInstanceRegistry  : Registered instance HELLO-SERVICE/192.168.0.14:hello-service:8091 with status UP (replication=true)
```

3. Go to http://localhost:8761/ and check the servers are included  as instance of the application `hello-service`

4. Run Spring Cloud Gateway

```shell
SERVER_PORT=8883 ./gradlew :3-eureka-service-disc:bootRun
```

5.Test Spring Cloud Gateway balancer

```shell
curl localhost:8883/hello
```

```
{ "message": "hello world from port 8090!"}%
```

```shell
curl localhost:8883/hello
```

```
{ "message": "hello world from port 8091!"}%
```

6. Mark server 1 as unhealthy sending PUT request to http://localhost:8090/status/false

```shell
curl localhost:8090/status/false -X PUT
```

You should see in the Eureka dashboard that there is only one instance available, and you will see some logs messages complaining that service on port `8090` is not available.
The health check is not immediate, so you might need to wait a few seconds to see the instance marked as DOWN.

7. Stop all the servers started in the previous steps

## Custom Filter at Route level (dynamic approach)

As you have seen, Spring Cloud Gateway offers an option for creating your own custom filters. It also lets you apply filters and change routes without restarting your gateway.

In this section, you can see a custom filter implementation that sets up load balancing and health checks of your services by using Spring Cloud Gateway route configuration.

If you already have a service discovery server in your project this might not be your best option. If not, this is a simple and cheap way to integrate two great features in your project.

```yaml
    spring:
      application:
        name: custom-service-disc
      cloud:
        loadbalancer:
          configurations: health-check # Note: required for enabling SDC with health checks - remove this line if you want to reproduce issues because not using health checks in LB
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

The new `LoadBalancer` route filter lets you configure the upstream service endpoints associated with the `lb://hello-service` load balancer URI:

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

If a route matches the `lb://<service-host>` pattern, the `LoadBalancerGatewayFilterFactory` will associate all the upstream service endpoints coming from the filter configuration to the `service-host`.

Under the hood, a new `ReactiveCustomDiscoveryClient` discovery client implementation has been included to manage upstream service endpoints in our code. 
Spring detects such a bean and prioritizes it in the list of [DiscoveryClient](https://github.com/spring-cloud/spring-cloud-commons/blob/main/spring-cloud-commons/src/main/java/org/springframework/cloud/client/discovery/DiscoveryClient.java) used to determine available endpoints.

### Trying out

1. Run servers
```shell
# Run server 1
SERVER_PORT=8090 ./gradlew :service:bootRun
```
```shell
# Run server 2
SERVER_PORT=8091 ./gradlew :service:bootRun
```

2. Check http://localhost:8090/actuator/health is "UP"

```shell
curl http://localhost:8090/actuator/health
```

```
{"status":"UP"}
```

3. Test http://localhost:8080/hello responds 200 OK

```shell
curl localhost:8090/hello
```

```
{ "message": "hello world!"}%
```

4. Run Spring Cloud Gateway

```shell
SERVER_PORT=8882 ./gradlew :2-custom-service-disc:bootRun
```

5. Test Spring Cloud Gateway balancer

```shell
curl localhost:8882/hello
```

```
{ "message": "hello world from port 8090!"}%
```

```shell
curl localhost:8882/hello
```

```
{ "message": "hello world from port 8091!"}%
```

You could need to run multiple times the previous commands to get a response from a different server.

6. Mark server 1 as unhealthy sending PUT request to http://localhost:8090/status/false

```shell
curl localhost:8090/status/false -X PUT
```

7. Check http://localhost:8090/actuator/status is "DOWN"

```shell
curl http://localhost:8090/actuator/health
```

```
{"status":"DOWN"}
```

8. Run multiple times a GET request to http://localhost:8881/hello and see that you only gets responds from port 8091

You could receive one response on port `8090` owing to the healthcheck not having checked the endpoint when you send the request. 
The interval can be modified in the `spring.cloud.loadbalancer.health-check.interval` property.

Also, you can see some messages that describe one of the upstream endpoints as not healthy, and, therefore, it is unavailable.

```
2023-05-08 15:59:53.151 DEBUG 9906 --- [ctor-http-nio-2] r.n.http.client.HttpClientOperations     : [12d42e83-77, L:/127.0.0.1:57439 - R:localhost/127.0.0.1:8090] Received response (auto-read:false) : RESPONSE(decodeResult: success, version: HTTP/1.1)
HTTP/1.1 503 Service Unavailable
```

```shell
curl localhost:8882/hello
```

```
{ "message": "hello world from port 8091!"}%
```

9. Mark server 2 as unhealthy sending PUT request to http://localhost:8091/status/false

```shell
curl localhost:8091/status/false -X PUT
```

10. Run some GET requests to http://localhost:8881/hello and see that it responds "503 Service Unavailable"

```shell
curl localhost:8882/hello
```

```
{"timestamp":"2023-05-08T14:07:48.704+00:00","path":"/hello","status":503,"error":"Service Unavailable","requestId":"6b5d6010-199"}%
```

11. Stop all the servers started in the previous steps

### Next steps

In this post, you have seen multiple ways to get load balancing and active health checks in your projects.
* From the static approach for basic projects or proof of concepts where the number of upstream services doesn’t change.
* As a more dynamic approach, using Eureka or Spring Cloud Gateway filters.

To sum up, you have also seen that the Spring Cloud Gateway approach is a great option if you do not need to add an extra component to your architecture.

# Additional Resources

Want to learn more about Spring Cloud? Join us virtually at [Spring Academy](https://spring.academy)! 
Want to get **active health check** just by adding a property in your route without getting your hands dirty? 
Take a look at our [commercial platform with Kubernetes](https://docs.vmware.com/en/VMware-Spring-Cloud-Gateway-for-Kubernetes/index.html) support.
