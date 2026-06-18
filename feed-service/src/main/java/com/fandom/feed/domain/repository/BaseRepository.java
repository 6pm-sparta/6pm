package com.fandom.feed.domain.repository;

import java.util.List;
import java.util.Optional;

public interface BaseRepository<T, ID> {
    T save(T entity);
    List<T> saveAll(Iterable<T> entities);
    Optional<T> findById(ID id);
}