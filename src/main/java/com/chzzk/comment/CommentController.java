package com.chzzk.comment;

import com.chzzk.broadcast.Broadcast;
import com.chzzk.broadcast.BroadcastRepository;
import com.chzzk.member.Member;
import com.chzzk.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CommentController {
    private final CommentRepository commentRepository;
    private final BroadcastRepository broadcastRepository;
    private final MemberRepository memberRepository;

    public CommentController(CommentRepository commentRepository, BroadcastRepository broadcastRepository,
                             MemberRepository memberRepository) {
        this.commentRepository = commentRepository;
        this.broadcastRepository = broadcastRepository;
        this.memberRepository = memberRepository;
    }

    @PostMapping("/broadcasts/{id}/comments")
    public String create(@PathVariable Long id, @RequestParam String content, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인해야 댓글을 쓸 수 있어요");
        }
        Broadcast broadcast = broadcastRepository.findById(id).orElseThrow();
        Member author = memberRepository.findById(memberId).orElseThrow();
        commentRepository.save(new Comment(broadcast, author, content));
        return "redirect:/broadcasts/" + id;
    }
}