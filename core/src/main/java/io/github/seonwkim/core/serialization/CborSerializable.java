package io.github.seonwkim.core.serialization;

/**
 * Marker interface for classes that should be serialized using CBOR (Concise Binary Object
 * Representation). Classes implementing this interface will be automatically serialized using
 * Pekko's Jackson CBOR serializer. CBOR is a binary data format that is more compact and efficient
 * than JSON for many use cases.
 */
public interface CborSerializable {}
