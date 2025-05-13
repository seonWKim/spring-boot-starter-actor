package io.github.seonwkim.core.utils;

import java.util.ArrayList;
import java.util.List;

public class IteratorUtils {

    public static <T> List<T> fromIterable(Iterable<T> iterable) {
        final List<T> result = new ArrayList<>();
        iterable.forEach(result::add);
        return result;
    }
}
