package com.example.alert_service;

import com.example.alert_service.repository.AlertLogRepository;
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
class AlertServiceApplicationTests {

	@MockBean
	AlertLogRepository alertLogRepository;

	@Test
	void contextLoads() {
	}
}