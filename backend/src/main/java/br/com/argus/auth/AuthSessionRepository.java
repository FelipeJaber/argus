package br.com.argus.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<AuthSession> findWithUserById(UUID id);

    /** Consulta executada a cada requisição autenticada (busca por chave primária). */
    @Query("""
            select count(s) > 0 from AuthSession s
            where s.id = :id and s.revokedAt is null and s.expiresAt > :now
            """)
    boolean isActive(@Param("id") UUID id, @Param("now") Instant now);

    @Query("""
            select s from AuthSession s
            where s.user.id = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.createdAt desc
            """)
    List<AuthSession> findActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.user.id = :userId and s.revokedAt is null
            """)
    int revokeAllByUser(@Param("userId") UUID userId, @Param("now") Instant now,
            @Param("reason") RevocationReason reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.user.id = :userId and s.revokedAt is null and s.id <> :keep
            """)
    int revokeAllByUserExcept(@Param("userId") UUID userId, @Param("keep") UUID keep, @Param("now") Instant now,
            @Param("reason") RevocationReason reason);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :threshold or s.revokedAt < :threshold")
    int deleteStaleBefore(@Param("threshold") Instant threshold);
}
