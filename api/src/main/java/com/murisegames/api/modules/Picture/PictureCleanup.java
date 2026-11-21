package com.murisegames.api.modules.Picture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class PictureCleanup {

    private final PictureTransactions transactions;

    @Scheduled(
            fixedDelayString = "PT2M",
            initialDelayString = "PT1M"
    )
    public void removeUnusedPictures() {
        for (var id : transactions.cleanupCandidates()) {
            try {
                transactions.cleanup(id);
            } catch (RuntimeException exception) {
                // Leave the database status unchanged so the next run retries.
                log.warn("Picture cleanup will retry; assetId={}", id);
            }
        }
    }
}