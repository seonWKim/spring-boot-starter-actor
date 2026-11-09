package io.github.seonwkim.actor.ddata;

/**
 * A read or write of replicated data did not reach the nodes its {@link Consistency} asked for in
 * time. A write that times out is still applied on this node and spreads to the others later; a read
 * that times out returns nothing.
 */
public class ReplicationTimeoutException extends RuntimeException {

    public ReplicationTimeoutException(String message) {
        super(message);
    }
}
