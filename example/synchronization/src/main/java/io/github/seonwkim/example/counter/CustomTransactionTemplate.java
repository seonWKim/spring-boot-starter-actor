package io.github.seonwkim.example.counter;

import jakarta.transaction.Transactional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class CustomTransactionTemplate {

    @Transactional
    public <T> T runInTransaction(Supplier<T> block) {
        return block.get();
    }
}
