package com.chzzk.broadcast;

import com.chzzk.comment.CommentRepository;
import com.chzzk.donation.Donation;
import com.chzzk.donation.DonationRepository;
import com.chzzk.donation.DonationStatus;
import com.chzzk.member.Member;
import com.chzzk.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequestMapping("/broadcasts")
public class BroadcastController {
    private final BroadcastRepository broadcastRepository;
    private final MemberRepository memberRepository;
    private final CommentRepository commentRepository;
    private final DonationRepository donationRepository;   // ← 새로 추가

    public BroadcastController(BroadcastRepository broadcastRepository, MemberRepository memberRepository,
                               CommentRepository commentRepository, DonationRepository donationRepository) {
        this.broadcastRepository = broadcastRepository;
        this.memberRepository = memberRepository;
        this.commentRepository = commentRepository;
        this.donationRepository = donationRepository;      // ← 새로 추가
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("broadcasts", broadcastRepository.findAll());
        return "broadcast/list";
    }

    @GetMapping("/new")
    public String form() {
        return "broadcast/form";
    }

    @PostMapping
    public String create(@RequestParam String title, @RequestParam String videoPath, HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) return "redirect:/login";

        Member owner = memberRepository.findById(memberId).orElseThrow();
        Broadcast broadcast = broadcastRepository.save(new Broadcast(title, videoPath, owner));
        return "redirect:/broadcasts/" + broadcast.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("broadcast", broadcastRepository.findById(id).orElseThrow());
        model.addAttribute("comments", commentRepository.findByBroadcastIdOrderByIdAsc(id));

        // ↓ 새로 추가: 확정(DONE)된 후원만 + 합계
        List<Donation> donations = donationRepository.findByBroadcastIdAndStatusOrderByIdDesc(id, DonationStatus.DONE);
        model.addAttribute("donations", donations);
        model.addAttribute("donationTotal", donations.stream().mapToLong(Donation::getAmount).sum());
        return "broadcast/detail";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session) {
        Broadcast broadcast = broadcastRepository.findById(id).orElseThrow();
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null || broadcast.getOwner() == null || !broadcast.getOwner().getId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방송 주인만 삭제할 수 있어요");
        }
        broadcastRepository.delete(broadcast);
        return "redirect:/broadcasts";
    }
}