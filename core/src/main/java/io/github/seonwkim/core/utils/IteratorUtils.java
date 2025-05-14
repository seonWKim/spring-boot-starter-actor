package io.github.seonwkim.core.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for working with iterables and iterators. This class provides methods for
 * converting between different collection types.
 */
public class IteratorUtils {

	/**
	 * Converts an Iterable to a List by iterating through all elements.
	 *
	 * @param iterable The iterable to convert
	 * @param <T> The type of elements in the iterable
	 * @return A new List containing all elements from the iterable
	 */
	public static <T> List<T> fromIterable(Iterable<T> iterable) {
		final List<T> result = new ArrayList<>();
		iterable.forEach(result::add);
		return result;
	}
}
