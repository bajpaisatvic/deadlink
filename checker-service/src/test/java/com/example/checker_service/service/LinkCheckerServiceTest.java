package com.example.checker_service.service;

import com.example.checker_service.dto.LinkCheckJob;
import com.example.checker_service.dto.LinkStatusChanged;
import com.example.checker_service.model.LinkCheckResult;
import com.example.checker_service.model.LinkStatusSnapshot;
import com.example.checker_service.repository.LinkCheckResultRepository;
import com.example.checker_service.repository.LinkStatusSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LinkCheckerServiceTest {

    @Mock private LinkCheckResultRepository linkCheckResultRepository;
    @Mock private LinkStatusSnapshotRepository linkStatusSnapshotRepository;

    private LinkCheckerService linkCheckerService;

    @BeforeEach
    void setUp() {
        linkCheckerService = spy(new LinkCheckerService(linkCheckResultRepository, linkStatusSnapshotRepository));
        when(linkCheckResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkStatusSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private LinkCheckJob job() {
        return new LinkCheckJob(UUID.randomUUID(), UUID.randomUUID(), "https://example.com");
    }

    private HttpURLConnection mockConn(int responseCode) throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(responseCode);
        return conn;
    }

    // --- status classification ---

    @Test
    void check_200_classifiedAsHealthy() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(200)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result).isPresent();
        assertThat(result.get().getNewStatus()).isEqualTo("HEALTHY");
        assertThat(result.get().getHttpStatus()).isEqualTo(200);
    }

    @Test
    void check_299_classifiedAsHealthy() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(299)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result.get().getNewStatus()).isEqualTo("HEALTHY");
    }

    @Test
    void check_301_classifiedAsRedirected_capturesLocationHeader() throws Exception {
        LinkCheckJob job = job();
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenReturn(301);
        when(conn.getHeaderField("Location")).thenReturn("https://new.example.com");
        doReturn(conn).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result.get().getNewStatus()).isEqualTo("REDIRECTED");
        ArgumentCaptor<LinkCheckResult> captor = ArgumentCaptor.forClass(LinkCheckResult.class);
        verify(linkCheckResultRepository).save(captor.capture());
        assertThat(captor.getValue().getRedirectUrl()).isEqualTo("https://new.example.com");
    }

    @Test
    void check_404_classifiedAsBroken() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(404)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result.get().getNewStatus()).isEqualTo("BROKEN");
        assertThat(result.get().getHttpStatus()).isEqualTo(404);
    }

    @Test
    void check_500_classifiedAsBroken() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(500)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result.get().getNewStatus()).isEqualTo("BROKEN");
    }

    @Test
    void check_socketTimeout_classifiedAsTimeout() throws Exception {
        LinkCheckJob job = job();
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenThrow(new SocketTimeoutException("timed out"));
        doReturn(conn).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result.get().getNewStatus()).isEqualTo("TIMEOUT");
        assertThat(result.get().getHttpStatus()).isNull();
    }

    @Test
    void check_unknownHost_classifiedAsBroken() throws Exception {
        LinkCheckJob job = job();
        doThrow(new UnknownHostException("bad.host")).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result.get().getNewStatus()).isEqualTo("BROKEN");
        assertThat(result.get().getHttpStatus()).isNull();
    }

    @Test
    void check_ioException_classifiedAsBroken() throws Exception {
        LinkCheckJob job = job();
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenThrow(new IOException("network error"));
        doReturn(conn).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result.get().getNewStatus()).isEqualTo("BROKEN");
    }

    // --- status-change detection ---

    @Test
    void check_firstCheck_noSnapshot_alwaysReturnsStatusChanged() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(200)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result).isPresent();
        assertThat(result.get().getPreviousStatus()).isNull();
    }

    @Test
    void check_statusUnchanged_returnsEmptyOptional() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(200)).when(linkCheckerService).openConnection(job.getUrl());
        LinkStatusSnapshot existing = LinkStatusSnapshot.builder()
                .linkId(job.getLinkId()).currentStatus("HEALTHY")
                .lastChangedAt(LocalDateTime.now()).consecutiveFailures(0).build();
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.of(existing));

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result).isEmpty();
    }

    @Test
    void check_statusChanged_returnsLinkStatusChangedWithBothStatuses() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(404)).when(linkCheckerService).openConnection(job.getUrl());
        LinkStatusSnapshot existing = LinkStatusSnapshot.builder()
                .linkId(job.getLinkId()).currentStatus("HEALTHY")
                .lastChangedAt(LocalDateTime.now()).consecutiveFailures(0).build();
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.of(existing));

        Optional<LinkStatusChanged> result = linkCheckerService.check(job);

        assertThat(result).isPresent();
        assertThat(result.get().getPreviousStatus()).isEqualTo("HEALTHY");
        assertThat(result.get().getNewStatus()).isEqualTo("BROKEN");
    }

    // --- consecutive failures tracking ---

    @Test
    void check_brokenStatus_incrementsConsecutiveFailures() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(503)).when(linkCheckerService).openConnection(job.getUrl());
        LinkStatusSnapshot existing = LinkStatusSnapshot.builder()
                .linkId(job.getLinkId()).currentStatus("BROKEN")
                .lastChangedAt(LocalDateTime.now()).consecutiveFailures(2).build();
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.of(existing));

        linkCheckerService.check(job);

        ArgumentCaptor<LinkStatusSnapshot> captor = ArgumentCaptor.forClass(LinkStatusSnapshot.class);
        verify(linkStatusSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    void check_timeoutStatus_incrementsConsecutiveFailures() throws Exception {
        LinkCheckJob job = job();
        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getResponseCode()).thenThrow(new SocketTimeoutException("timed out"));
        doReturn(conn).when(linkCheckerService).openConnection(job.getUrl());
        LinkStatusSnapshot existing = LinkStatusSnapshot.builder()
                .linkId(job.getLinkId()).currentStatus("TIMEOUT")
                .lastChangedAt(LocalDateTime.now()).consecutiveFailures(1).build();
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.of(existing));

        linkCheckerService.check(job);

        ArgumentCaptor<LinkStatusSnapshot> captor = ArgumentCaptor.forClass(LinkStatusSnapshot.class);
        verify(linkStatusSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getConsecutiveFailures()).isEqualTo(2);
    }

    @Test
    void check_healthyAfterFailures_resetsConsecutiveFailures() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(200)).when(linkCheckerService).openConnection(job.getUrl());
        LinkStatusSnapshot existing = LinkStatusSnapshot.builder()
                .linkId(job.getLinkId()).currentStatus("BROKEN")
                .lastChangedAt(LocalDateTime.now()).consecutiveFailures(5).build();
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.of(existing));

        linkCheckerService.check(job);

        ArgumentCaptor<LinkStatusSnapshot> captor = ArgumentCaptor.forClass(LinkStatusSnapshot.class);
        verify(linkStatusSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getConsecutiveFailures()).isEqualTo(0);
    }

    // --- persistence ---

    @Test
    void check_alwaysSavesLinkCheckResult() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(200)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        linkCheckerService.check(job);

        verify(linkCheckResultRepository).save(any(LinkCheckResult.class));
    }

    @Test
    void check_alwaysSavesSnapshot() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(200)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        linkCheckerService.check(job);

        verify(linkStatusSnapshotRepository).save(any(LinkStatusSnapshot.class));
    }

    @Test
    void check_savedResultContainsCorrectLinkId() throws Exception {
        LinkCheckJob job = job();
        doReturn(mockConn(200)).when(linkCheckerService).openConnection(job.getUrl());
        when(linkStatusSnapshotRepository.findByLinkId(job.getLinkId())).thenReturn(Optional.empty());

        linkCheckerService.check(job);

        ArgumentCaptor<LinkCheckResult> captor = ArgumentCaptor.forClass(LinkCheckResult.class);
        verify(linkCheckResultRepository).save(captor.capture());
        assertThat(captor.getValue().getLinkId()).isEqualTo(job.getLinkId());
        assertThat(captor.getValue().getStatus()).isEqualTo("HEALTHY");
    }
}
