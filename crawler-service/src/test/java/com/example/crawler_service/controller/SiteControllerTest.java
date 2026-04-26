package com.example.crawler_service.controller;

import com.example.crawler_service.dto.SiteDetailsResponse;
import com.example.crawler_service.dto.SiteRegistrationRequest;
import com.example.crawler_service.dto.SiteRegistrationResponse;
import com.example.crawler_service.service.SiteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SiteController.class)
public class SiteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SiteService siteService;

    private SiteRegistrationRequest buildRequest() {
        SiteRegistrationRequest req = new SiteRegistrationRequest();
        req.setName("Test Site");
        req.setRootUrl("https://example.com");
        req.setCrawlDepth(2);
        req.setCheckIntervalHours(24);
        return req;
    }

    @Test
    void registerSite_returns201WithSiteIdAndMessage() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(siteService.registerSite(any()))
                .thenReturn(new SiteRegistrationResponse(siteId, "Site registered successfully. Crawl will start shortly."));

        mockMvc.perform(post("/api/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteId").value(siteId.toString()))
                .andExpect(jsonPath("$.message").value("Site registered successfully. Crawl will start shortly."));
    }

    @Test
    void registerSite_delegatesToSiteService() throws Exception {
        when(siteService.registerSite(any()))
                .thenReturn(new SiteRegistrationResponse(UUID.randomUUID(), "ok"));

        mockMvc.perform(post("/api/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated());

        verify(siteService).registerSite(any(SiteRegistrationRequest.class));
    }

    @Test
    void registerSite_returns400WhenUrlAlreadyRegistered() throws Exception {
        when(siteService.registerSite(any()))
                .thenThrow(new IllegalArgumentException("Site already registered: https://example.com"));

        mockMvc.perform(post("/api/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Site already registered: https://example.com"));
    }

    @Test
    void getSiteDetails_returns200WithFullSiteData() throws Exception {
        UUID siteId = UUID.randomUUID();
        SiteDetailsResponse response = new SiteDetailsResponse(
                siteId, "Test Site", "https://example.com",
                2, 24,
                LocalDateTime.of(2024, 1, 15, 10, 0),
                LocalDateTime.of(2024, 1, 15, 12, 0),
                150L);

        when(siteService.getSiteDetails(siteId)).thenReturn(response);

        mockMvc.perform(get("/api/sites/{siteId}", siteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteId").value(siteId.toString()))
                .andExpect(jsonPath("$.name").value("Test Site"))
                .andExpect(jsonPath("$.rootUrl").value("https://example.com"))
                .andExpect(jsonPath("$.crawlDepth").value(2))
                .andExpect(jsonPath("$.checkIntervalHours").value(24))
                .andExpect(jsonPath("$.totalLinksDiscovered").value(150));
    }

    @Test
    void getSiteDetails_returns400WhenSiteNotFound() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(siteService.getSiteDetails(siteId))
                .thenThrow(new IllegalArgumentException("Site not found: " + siteId));

        mockMvc.perform(get("/api/sites/{siteId}", siteId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Site not found: " + siteId));
    }

    @Test
    void getSiteDetails_acceptsUuidPathVariable() throws Exception {
        UUID siteId = UUID.randomUUID();
        SiteDetailsResponse response = new SiteDetailsResponse(
                siteId, "My Site", "https://example.com",
                1, 12, null, null, 0L);

        when(siteService.getSiteDetails(siteId)).thenReturn(response);

        mockMvc.perform(get("/api/sites/{siteId}", siteId))
                .andExpect(status().isOk());

        verify(siteService).getSiteDetails(siteId);
    }
}
