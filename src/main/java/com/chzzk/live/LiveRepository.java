package com.chzzk.live;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveRepository extends JpaRepository<Live, Long> {
    List<Live> findAllByOrderByIdDesc();
}