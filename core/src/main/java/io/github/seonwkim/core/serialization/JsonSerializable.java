package io.github.seonwkim.core.serialization;

/**
 * Marker interface for classes that should be serialized using JSON (JavaScript Object Notation).
 * Classes implementing this interface will be automatically serialized using Pekko's Jackson JSON
 * serializer. JSON is a text-based data format that is human-readable and widely supported across
 * different platforms.
 */
public interface JsonSerializable {}
