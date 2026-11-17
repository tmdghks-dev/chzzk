package com.chzzk.broadcast;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Broadcast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String videoPath;
    private LocalDateTime createdAt;

    protected Broadcast(){

    }
    public Broadcast(String title, String videoPath){
        this.title = title;
        this.videoPath = videoPath;
        this.createdAt = LocalDateTime.now();
    }
    public Long getId(){
        return id;
    }
    public String getTitle(){
        return title;
    }
    public String getVideoPath(){
        return videoPath;
    }
    public LocalDateTime getCreatedAt(){
        return createdAt;
    }
}
