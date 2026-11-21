package com.karan.ev_charging_backend.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByStation_IdOrderByCreatedAtDesc(Long stationId);

    Optional<Review> findByUser_IdAndStation_Id(Long userId, Long stationId);

    boolean existsByUser_IdAndStation_Id(Long userId, Long stationId);

    long countByStation_Id(Long stationId);
}