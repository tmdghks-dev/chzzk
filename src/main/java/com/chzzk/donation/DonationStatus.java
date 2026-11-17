package com.chzzk.donation;

public enum DonationStatus {
    READY,     // 주문서만 만든 상태 (아직 결제 전)
    DONE,      // 토스 승인까지 끝난 확정 후원
    FAILED,    // 결제 실패
    CANCELED   // 사용자가 결제창을 닫음
}