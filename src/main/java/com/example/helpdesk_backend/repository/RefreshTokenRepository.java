package com.example.helpdesk_backend.repository;

import com.example.helpdesk_backend.entity.RefreshToken;
import com.example.helpdesk_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenId(String tokenId);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserAndRevokedAtIsNull(User user);
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
    void deleteByUser(User user);
    boolean existsByTokenIdAndRevokedAtIsNull(String tokenId);
}
