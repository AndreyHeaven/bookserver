package ru.andreyheaven.bookserver.repository;

import org.springframework.cache.annotation.*;
import org.springframework.data.jdbc.repository.query.*;
import org.springframework.data.repository.*;
import org.springframework.data.repository.query.*;
import ru.andreyheaven.bookserver.domain.*;
import java.util.*;

public interface GenreRepository extends CrudRepository<Genre, String> {

    @Override
    @Cacheable("genre")
    Optional<Genre> findById(String id);

    @Query("select g.* from genres g full join genres_genres gg on g.code = gg.genre_id where gg.parent_id is null")
    List<Genre> findParentGenres();

    @Query("select g.* from genres g full join genres_genres gg on g.code = gg.genre_id where gg.parent_id = :code")
    List<Genre> findByParent(@Param("code") String code);
}
