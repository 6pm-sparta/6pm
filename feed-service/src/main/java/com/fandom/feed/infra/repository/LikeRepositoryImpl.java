package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.domain.util.IdGenerator;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class LikeRepositoryImpl extends BaseRepositoryImpl<Like, UUID, JpaLikeRepository> implements LikeRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public LikeRepositoryImpl(JpaLikeRepository jpaRepository, JdbcTemplate jdbcTemplate, IdGenerator idGenerator) {
        super(jpaRepository);
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
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
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (post_id, user_id) DO NOTHING
        """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Like like = likes.get(i);
                ps.setObject(1, idGenerator.generate());
                ps.setObject(2, like.getPostId());
                ps.setObject(3, like.getUserId());
            }

            @Override
            public int getBatchSize() {
                return likes.size();
            }
        });
    }

    @Override
    public List<Like> findAllByPostId(UUID postId) {
        return jpaRepository.findAllByPostId(postId);
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        jpaRepository.deleteAllByUserId(userId);
    }

    @Override
    public List<UUID> findPostIdsByUserId(UUID userId) {
        return jpaRepository.findPostIdsByUserId(userId);
    }

    @Override
    public void deleteAllByPostIdIn(List<UUID> postIds) {
        jpaRepository.deleteAllByPostIdIn(postIds);
    }
}