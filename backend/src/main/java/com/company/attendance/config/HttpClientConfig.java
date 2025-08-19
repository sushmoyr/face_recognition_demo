package com.company.attendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

/**
 * Configuration for HTTP client beans.
 */
@Configuration
public class HttpClientConfig {

	/**
	 * RestTemplate bean for HTTP client operations.
	 */
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.setConnectTimeout(java.time.Duration.ofSeconds(10))
			.setReadTimeout(java.time.Duration.ofSeconds(30))
			.build();
	}

}
