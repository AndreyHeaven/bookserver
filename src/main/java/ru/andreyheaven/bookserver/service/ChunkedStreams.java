package ru.andreyheaven.bookserver.service;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public final class ChunkedStreams {
    private ChunkedStreams(){}
    @SuppressWarnings("SameParameterValue")
    public static <T> Stream<List<T>> chunks(Stream<T> sourceStream, int size) {
        var source = sourceStream.spliterator();
        return StreamSupport.stream(new Spliterator<>() {
            final List<T> buf = new ArrayList<>();

            @Override
            public boolean tryAdvance(Consumer<? super List<T>> action) {
                while (buf.size() < size) {
                    if (!source.tryAdvance(buf::add)) {
                        if (!buf.isEmpty()) {
                            action.accept(List.copyOf(buf));
                            buf.clear();
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
                action.accept(List.copyOf(buf));
                buf.clear();
                return true;
            }

            @Override
            public Spliterator<List<T>> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                var sourceSize = source.estimateSize();
                return sourceSize / size + (sourceSize % size != 0 ? 1 : 0);
            }

            @Override
            public int characteristics() {
                return NONNULL | ORDERED;
            }
        }, false);
    }
}