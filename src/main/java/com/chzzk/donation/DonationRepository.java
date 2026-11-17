package com.chzzk.donation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    Optional<Donation> findByOrderId(String orderId);
    List<Donation> findByBroadcastIdAndStatusOrderByIdDesc(Long broadcastId, DonationStatus status);
}