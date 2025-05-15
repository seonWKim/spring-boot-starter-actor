package io.github.seonwkim.example.counter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Implementation of CounterService that uses database locking for synchronization. Uses pessimistic
 * locking to ensure that only one thread can increment a counter at a time.
 */
@Service
public class DbCounterService implements CounterService {

	private static final Logger logger = LoggerFactory.getLogger(DbCounterService.class);

	private final CounterRepository counterRepository;
	private final CustomTransactionTemplate customTransactionTemplate;

	/**
	 * Creates a new DbCounterServiceImpl with the given repository.
	 *
	 * @param counterRepository The repository for accessing Counter entities
	 */
	public DbCounterService(
			CounterRepository counterRepository, CustomTransactionTemplate customTransactionTemplate) {
		this.counterRepository = counterRepository;
		this.customTransactionTemplate = customTransactionTemplate;
	}

	/**
	 * Increments the counter value by 1 and returns the new value. Uses database pessimistic locking
	 * to ensure synchronization. The blocking logic is delegated to a boundedElastic scheduler.
	 *
	 * @param counterId The ID of the counter to increment
	 */
	@Override
	public void increment(String counterId) {
		incrementInternal(counterId);
	}

	/**
	 * Gets the current value of the counter. The blocking logic is delegated to a boundedElastic
	 * scheduler.
	 *
	 * @param counterId The ID of the counter to get
	 * @return A Mono containing the current counter value
	 */
	@Override
	public Mono<Long> getValue(String counterId) {
		return Mono.fromCallable(() -> getValueInternal(counterId))
				.subscribeOn(Schedulers.boundedElastic());
	}

	public Long incrementInternal(String counterId) {
		logger.debug("Incrementing counter with ID: {}", counterId);

		return customTransactionTemplate.runInTransaction(
				() -> {
					Counter counter =
							counterRepository
									.findByIdWithLock(counterId)
									.orElseGet(
											() -> {
												logger.debug(
														"Counter not found, creating new counter with ID: {}", counterId);
												return counterRepository.save(new Counter(counterId, 0));
											});

					long newValue = counter.increment();
					counterRepository.save(counter);

					logger.debug("Counter with ID: {} incremented to: {}", counterId, newValue);
					return newValue;
				});
	}

	public Long getValueInternal(String counterId) {
		logger.debug("Getting value for counter with ID: {}", counterId);
		return counterRepository.findById(counterId).map(Counter::getValue).orElse(0L);
	}
}
