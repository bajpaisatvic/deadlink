package com.example.checker_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
		KafkaAutoConfiguration.class,
		DataSourceAutoConfiguration.class,
		FlywayAutoConfiguration.class
})
class CheckerServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}