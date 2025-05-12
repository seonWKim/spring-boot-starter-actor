package io.github.seonwkim.example.counter;

import java.util.function.Supplier;
import javax.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class CustomTransactionTemplate {

	@Transactional
	public <T> T runInTransaction(Supplier<T> block) {
		return block.get();
	}
}
