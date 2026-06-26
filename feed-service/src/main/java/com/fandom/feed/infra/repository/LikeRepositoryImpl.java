package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import com.fasterxml.uuid.Generators;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class LikeRepositoryImpl extends BaseRepositoryImpl<Like, UUID, JpaLikeRepository> implements LikeRepository {
    private final JdbcTemplate jdbcTemplate;

    public LikeRepositoryImpl(JpaLikeRepository jpaRepository, JdbcTemplate jdbcTemplate) {
        super(jpaRepository);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Like> findAllByPostId(UUID postId) {
        return jpaRepository.findAllByPostId(postId);
    }

    @Override
    public void deleteByPostIdAndUserId(UUID postId, UUID userId) {
        jpaRepository.deleteByPostIdAndUserId(postId, userId);
    }

    @Override
    public List<Like> findByCursorAndUserId(UUID cursor, ReactionSort sort, UUID userId) {
        Pageable pageable = PageRequest.of(0, FeedPolicy.PAGE_SIZE + 1);

        return switch (sort) {
            case LATEST -> jpaRepository.findLatestByUserId(cursor, userId, pageable);
            case OLDEST -> jpaRepository.findOldestByUserId(cursor, userId, pageable);
        };
    }

    @Override
    public void deleteAllByPostId(UUID postId) {
        jpaRepository.deleteAllByPostId(postId);
    }

    @Override
    public Map<UUID, List<UUID>> findLikeUsersByPostIds(List<UUID> postIds) {
        return jpaRepository.findLikeUsersByPostIds(postIds)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (UUID) row[0],
                        Collectors.mapping(row -> (UUID) row[1], Collectors.toList())
                ));
    }

    @Override
    public void batchInsertOnConflictDoNothing(List<Like> likes) {
        String sql = """
            INSERT INTO likes (id, post_id, user_id, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (post_id, user_id) DO NOTHING
        """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Like like = likes.get(i);
                ps.setObject(1, Generators.timeBasedEpochGenerator().generate());
                ps.setObject(2, like.getPostId());
                ps.setObject(3, like.getUserId());
                ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return likes.size();
            }
        });
    }
}