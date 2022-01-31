package ru.andreyheaven.bookserver.repository;

import org.springframework.data.repository.*;
import ru.andreyheaven.bookserver.domain.*;

public interface GenreRepository extends CrudRepository<Genre, String> {
}
