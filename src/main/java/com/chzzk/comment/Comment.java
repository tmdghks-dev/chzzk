package com.chzzk.comment;

import com.chzzk.broadcast.Broadcast;
import com.chzzk.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Broadcast broadcast;   // 어느 방송의 댓글인지

    @ManyToOne
    private Member author;         // 누가 썼는지

    private String content;
    private LocalDateTime createdAt;

    protected Comment() {
    }

    public Comment(Broadcast broadcast, Member author, String content) {
        this.broadcast = broadcast;
        this.author = author;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Broadcast getBroadcast() { return broadcast; }
    public Member getAuthor() { return author; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}