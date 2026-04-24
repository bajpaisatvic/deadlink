package com.example.crawler_service;

import com.example.crawler_service.repository.DiscoveredLinkRepository;
import com.example.crawler_service.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
		KafkaAutoConfiguration.class,
		DataSourceAutoConfiguration.class,
		FlywayAutoConfiguration.class
})
class CrawlerServiceApplicationTests {

	@MockBean
	SiteRepository siteRepository;

	@MockBean
	DiscoveredLinkRepository discoveredLinkRepository;

	@Test
	void contextLoads() {
	}
}