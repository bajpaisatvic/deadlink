package com.example.checker_service;

import com.example.checker_service.repository.LinkCheckResultRepository;
import com.example.checker_service.repository.LinkStatusSnapshotRepository;
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
class CheckerServiceApplicationTests {

	@MockBean
	LinkCheckResultRepository linkCheckResultRepository;

	@MockBean
	LinkStatusSnapshotRepository linkStatusSnapshotRepository;

	@Test
	void contextLoads() {
	}
}