package com.chzzk.donation;

import com.chzzk.broadcast.Broadcast;
import com.chzzk.broadcast.BroadcastRepository;
import com.chzzk.member.Member;
import com.chzzk.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Controller
public class DonationController {
    private final DonationRepository donationRepository;
    private final BroadcastRepository broadcastRepository;
    private final MemberRepository memberRepository;
    private final TossClient tossClient;
    private final String clientKey;

    public DonationController(DonationRepository donationRepository, BroadcastRepository broadcastRepository,
                              MemberRepository memberRepository, TossClient tossClient,
                              @Value("${toss.client-key}") String clientKey) {
        this.donationRepository = donationRepository;
        this.broadcastRepository = broadcastRepository;
        this.memberRepository = memberRepository;
        this.tossClient = tossClient;
        this.clientKey = clientKey;
    }

    // ① 주문서 만들기 → 결제 화면
    @PostMapping("/broadcasts/{id}/donations")
    public String create(@PathVariable Long id, @RequestParam long amount, HttpSession session, Model model) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인해야 후원할 수 있어요");
        }
        Broadcast broadcast = broadcastRepository.findById(id).orElseThrow();
        if (broadcast.getOwner() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "방송 주인이 없는 방송이에요");
        }
        if (amount < 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "1,000원 이상 후원할 수 있어요");
        }
        Member donor = memberRepository.findById(memberId).orElseThrow();
        Donation donation = donationRepository.save(new Donation(broadcast, donor, amount));

        model.addAttribute("donation", donation);
        model.addAttribute("clientKey", clientKey);
        return "donation/checkout";
    }

    // ② 결제 성공 리다이렉트 → 서버가 검증하고 토스에 승인 요청
    @GetMapping("/donations/success")
    public String success(@RequestParam String paymentKey, @RequestParam String orderId,
                          @RequestParam long amount, Model model) {
        Donation donation = donationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 주문이에요"));
        Long broadcastId = donation.getBroadcast().getId();

        // 같은 성공 통지가 또 와도 이미 DONE이면 아무것도 안 함 (중복 적립 방지)
        if (donation.getStatus() == DonationStatus.DONE) {
            return "redirect:/broadcasts/" + broadcastId;
        }
        if (donation.getStatus() != DonationStatus.READY) {
            model.addAttribute("message", "이미 닫힌 주문이에요");
            return "donation/fail";
        }
        // 브라우저가 보낸 금액이 주문서와 다르면 거절 (금액 조작 방지)
        if (donation.getAmount() != amount) {
            donation.close(DonationStatus.FAILED);
            donationRepository.save(donation);
            model.addAttribute("message", "결제 금액이 주문과 달라요");
            return "donation/fail";
        }
        // 받는 사람이 지금도 방송 주인인지 확인
        if (!donation.getReceiver().getId().equals(donation.getBroadcast().getOwner().getId())) {
            donation.close(DonationStatus.FAILED);
            donationRepository.save(donation);
            model.addAttribute("message", "받는 사람이 방송 주인과 달라요");
            return "donation/fail";
        }

        Map result;
        try {
            result = tossClient.confirm(paymentKey, orderId, donation.getAmount());
        } catch (RestClientResponseException e) {
            model.addAttribute("message", "토스 승인 실패: " + e.getResponseBodyAsString());
            return "donation/fail";
        }

        // 토스 응답도 한 번 더 확인
        boolean ok = "DONE".equals(result.get("status"))
                && orderId.equals(result.get("orderId"))
                && ((Number) result.get("totalAmount")).longValue() == donation.getAmount();
        if (!ok) {
            model.addAttribute("message", "토스 승인 결과가 주문과 달라요");
            return "donation/fail";
        }

        donation.complete(paymentKey);
        donationRepository.save(donation);
        return "redirect:/broadcasts/" + broadcastId;
    }

    // ③ 결제 실패·취소 리다이렉트
    @GetMapping("/donations/fail")
    public String fail(@RequestParam(required = false) String code,
                       @RequestParam(required = false) String message,
                       @RequestParam(required = false) String orderId, Model model) {
        if (orderId != null) {
            donationRepository.findByOrderId(orderId)
                    .filter(d -> d.getStatus() == DonationStatus.READY)
                    .ifPresent(d -> {
                        d.close("PAY_PROCESS_CANCELED".equals(code) ? DonationStatus.CANCELED : DonationStatus.FAILED);
                        donationRepository.save(d);
                    });
        }
        model.addAttribute("message", message);
        return "donation/fail";
    }
}