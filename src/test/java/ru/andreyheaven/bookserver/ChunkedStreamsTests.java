package ru.andreyheaven.bookserver;

import org.junit.jupiter.api.*;
import ru.andreyheaven.bookserver.service.*;
import java.util.*;
import java.util.stream.*;

public class ChunkedStreamsTests {

    @Test
    void contextLoads() {
        final IntStream intStream = IntStream.rangeClosed(1, 100);
        final long count = ChunkedStreams.chunks(intStream.boxed(), 10).peek(integers -> Assertions.assertEquals(10, integers.size())).flatMap(Collection::stream).count();
        Assertions.assertEquals(100, count);

    }

}
