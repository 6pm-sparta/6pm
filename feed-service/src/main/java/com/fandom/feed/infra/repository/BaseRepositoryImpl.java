package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.repository.BaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public class BaseRepositoryImpl<T, ID, R extends JpaRepository<T, ID>> implements BaseRepository<T, ID> {
    protected final R jpaRepository;

    public BaseRepositoryImpl(R jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public T save(T entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public List<T> saveAll(Iterable<T> entities) { // 위임 코드
        return jpaRepository.saveAll(entities);
    }

    @Override
    public Optional<T> findById(ID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        return jpaRepository.findAllById(ids);
    }
}