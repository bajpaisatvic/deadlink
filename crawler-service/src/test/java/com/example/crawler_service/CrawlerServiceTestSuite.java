package com.example.crawler_service;

import com.example.crawler_service.controller.SiteControllerTest;
import com.example.crawler_service.service.CrawlerServiceTest;
import com.example.crawler_service.service.KafkaProducerServiceTest;
import com.example.crawler_service.service.SiteSchedulingServiceTest;
import com.example.crawler_service.service.SiteServiceTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        CrawlerServiceTest.class,
        SiteServiceTest.class,
        KafkaProducerServiceTest.class,
        SiteSchedulingServiceTest.class,
        SiteControllerTest.class,
        CrawlerServiceApplicationTests.class
})
public class CrawlerServiceTestSuite {
}
