package com.chzzk.live;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class Live {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private LiveStatus status;

    private String streamKey;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    protected Live() {
    }

    public Live(String title) {
        this.title = title;
        this.status = LiveStatus.ON_AIR;
        this.streamKey = UUID.randomUUID().toString();
        this.startedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(String key) {
        return streamKey.equals(key);
    }

    public void end() {
        this.status = LiveStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public boolean isOnAir() {
        return status == LiveStatus.ON_AIR;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LiveStatus getStatus() { return status; }
    public String getStreamKey() { return streamKey; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
}