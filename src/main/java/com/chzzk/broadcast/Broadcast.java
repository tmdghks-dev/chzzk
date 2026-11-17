package com.chzzk.broadcast;

import com.chzzk.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Broadcast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String videoPath;
    private LocalDateTime createdAt;

    @ManyToOne
    private Member owner;          // ← 새로 추가: 방송 주인

    protected Broadcast() {
    }

    public Broadcast(String title, String videoPath, Member owner) {   // ← owner 매개변수 추가
        this.title = title;
        this.videoPath = videoPath;
        this.owner = owner;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getVideoPath() { return videoPath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Member getOwner() { return owner; }   // ← 새로 추가
}