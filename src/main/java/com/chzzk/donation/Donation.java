package com.chzzk.donation;

import com.chzzk.broadcast.Broadcast;
import com.chzzk.member.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class Donation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderId;          // 우리가 만든 주문번호 (토스에 넘김)

    @Column(unique = true)
    private String paymentKey;       // 토스가 준 결제 번호 → 같은 결제가 두 번 저장되지 않게 unique

    @ManyToOne
    private Broadcast broadcast;
    @ManyToOne
    private Member donor;            // 후원한 사람
    @ManyToOne
    private Member receiver;         // 받는 사람 (방송 주인)

    private long amount;

    @Enumerated(EnumType.STRING)
    private DonationStatus status;

    private LocalDateTime createdAt;

    protected Donation() {
    }

    public Donation(Broadcast broadcast, Member donor, long amount) {
        this.orderId = UUID.randomUUID().toString();
        this.broadcast = broadcast;
        this.donor = donor;
        this.receiver = broadcast.getOwner();
        this.amount = amount;
        this.status = DonationStatus.READY;
        this.createdAt = LocalDateTime.now();
    }

    public void complete(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = DonationStatus.DONE;
    }

    public void close(DonationStatus status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getPaymentKey() { return paymentKey; }
    public Broadcast getBroadcast() { return broadcast; }
    public Member getDonor() { return donor; }
    public Member getReceiver() { return receiver; }
    public long getAmount() { return amount; }
    public DonationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}