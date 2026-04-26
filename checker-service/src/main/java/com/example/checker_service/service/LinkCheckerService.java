package com.example.checker_service.service;

import com.example.checker_service.dto.LinkCheckJob;
import com.example.checker_service.dto.LinkStatusChanged;
import com.example.checker_service.model.LinkCheckResult;
import com.example.checker_service.model.LinkStatusSnapshot;
import com.example.checker_service.repository.LinkCheckResultRepository;
import com.example.checker_service.repository.LinkStatusSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkCheckerService {

    private final LinkCheckResultRepository linkCheckResultRepository;
    private final LinkStatusSnapshotRepository linkStatusSnapshotRepository;

    public Optional<LinkStatusChanged> check(LinkCheckJob job) {
        log.info("Checking link: {}", job.getUrl());
        long startTime = System.currentTimeMillis();
        String status;
        Integer httpStatus = null;
        String redirectUrl = null;

        try {
            HttpURLConnection connection = openConnection(job.getUrl());

            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "DeadLink-Bot/1.0");

            httpStatus = connection.getResponseCode();

            if (httpStatus >= 200 && httpStatus < 300) {
                status = "HEALTHY";
            } else if (httpStatus >= 300 && httpStatus < 400) {
                status = "REDIRECTED";
                redirectUrl = connection.getHeaderField("Location");
            } else {
                status = "BROKEN";
            }

            connection.disconnect();
        } catch (UnknownHostException ex){
            log.warn("DNS failure for {}: {}", job.getUrl(), ex.getMessage());
            status = "BROKEN";
        } catch (SocketTimeoutException ex){
            log.warn("Timeout for {}: {}", job.getUrl(), ex.getMessage());
            status = "TIMEOUT";
        } catch (IOException ex){
            log.warn("IO error for {}: {}", job.getUrl(), ex.getMessage());
            status = "BROKEN";
        }

        int responseTime = (int) (System.currentTimeMillis() - startTime);

        LinkCheckResult result = LinkCheckResult.builder()
                .linkId(job.getLinkId())
                .httpStatus(httpStatus)
                .responseTime(responseTime)
                .status(status)
                .redirectUrl(redirectUrl)
                .build();

        linkCheckResultRepository.save(result);

        Optional<LinkStatusSnapshot> existingSnapshot =  linkStatusSnapshotRepository.findByLinkId(job.getLinkId());

        String previousStatus = existingSnapshot
                .map(LinkStatusSnapshot::getCurrentStatus)
                .orElse(null);

        LinkStatusSnapshot snapshot = existingSnapshot.orElse(
                LinkStatusSnapshot.builder()
                        .linkId(job.getLinkId())
                        .lastChangedAt(LocalDateTime.now())
                        .consecutiveFailures(0)
                        .build()
        );

        snapshot.setCurrentStatus(status);
        if (!status.equals(previousStatus)){
            snapshot.setLastChangedAt(LocalDateTime.now());
        }
        if (status.equals("BROKEN") || status.equals("TIMEOUT")){
            snapshot.setConsecutiveFailures(snapshot.getConsecutiveFailures() + 1);
        } else {
            snapshot.setConsecutiveFailures(0);
        }
        linkStatusSnapshotRepository.save(snapshot);

        if (previousStatus == null || !status.equals(previousStatus)){
            log.info("Status changed for {}: {} → {}", job.getUrl(), previousStatus, status);
            return Optional.of(new LinkStatusChanged(
                    job.getLinkId(), job.getSiteId(), job.getUrl(),
                    previousStatus, status, httpStatus, LocalDateTime.now()
            ));
        }

        return Optional.empty();
    }

    protected HttpURLConnection openConnection(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }
}
