package com.example.checker_service;

import com.example.checker_service.consumer.LinkCheckConsumerTest;
import com.example.checker_service.service.KafkaProducerServiceTest;
import com.example.checker_service.service.LinkCheckerServiceTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        LinkCheckerServiceTest.class,
        KafkaProducerServiceTest.class,
        LinkCheckConsumerTest.class,
        CheckerServiceApplicationTests.class
})
public class CheckerServiceTestSuite {
}
