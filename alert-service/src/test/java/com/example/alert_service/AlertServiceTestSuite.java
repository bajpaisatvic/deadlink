package com.example.alert_service;

import com.example.alert_service.consumer.LinkStatusConsumerTest;
import com.example.alert_service.controller.ReportControllerTest;
import com.example.alert_service.service.AlertServiceTest;
import com.example.alert_service.service.ReportServiceTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        AlertServiceTest.class,
        ReportServiceTest.class,
        LinkStatusConsumerTest.class,
        ReportControllerTest.class,
        AlertServiceApplicationTests.class
})
public class AlertServiceTestSuite {
}
