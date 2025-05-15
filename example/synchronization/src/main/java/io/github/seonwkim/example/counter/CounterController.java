package io.github.seonwkim.example.counter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller that exposes the counter functionality via HTTP endpoints. Provides APIs for
 * incrementing and getting counter values using different synchronization methods.
 */
@RestController
@RequestMapping("/counter")
public class CounterController {

	private final DbCounterService dbCounterService;
	private final RedisCounterService redisCounterService;
	private final ActorCounterService actorCounterService;

	/**
	 * Creates a new CounterController with the given services.
	 *
	 * @param dbCounterService The DB-based counter service
	 * @param redisCounterService The Redis-based counter service
	 * @param actorCounterService The actor-based counter service
	 */
	public CounterController(
			DbCounterService dbCounterService,
			RedisCounterService redisCounterService,
			ActorCounterService actorCounterService) {
		this.dbCounterService = dbCounterService;
		this.redisCounterService = redisCounterService;
		this.actorCounterService = actorCounterService;
	}

	/**
	 * Endpoint to increment a counter using DB locking.
	 *
	 * @param counterId The ID of the counter to increment
	 * @return A Mono containing the new counter value
	 */
	@GetMapping("/db/{counterId}/increment")
	public void incrementDbCounter(@PathVariable String counterId) {
		dbCounterService.increment(counterId);
	}

	/**
	 * Endpoint to get a counter value using DB locking.
	 *
	 * @param counterId The ID of the counter to get
	 * @return A Mono containing the current counter value
	 */
	@GetMapping("/db/{counterId}")
	public Mono<Long> getDbCounter(@PathVariable String counterId) {
		return dbCounterService.getValue(counterId);
	}

	/**
	 * Endpoint to increment a counter using Redis locking.
	 *
	 * @param counterId The ID of the counter to increment
	 */
	@GetMapping("/redis/{counterId}/increment")
	public void incrementRedisCounter(@PathVariable String counterId) {
		redisCounterService.increment(counterId);
	}

	/**
	 * Endpoint to get a counter value using Redis locking.
	 *
	 * @param counterId The ID of the counter to get
	 * @return A Mono containing the current counter value
	 */
	@GetMapping("/redis/{counterId}")
	public Mono<Long> getRedisCounter(@PathVariable String counterId) {
		return redisCounterService.getValue(counterId);
	}

	/**
	 * Endpoint to increment a counter using actor-based locking.
	 *
	 * @param counterId The ID of the counter to increment
	 * @return A Mono containing the new counter value
	 */
	@GetMapping("/actor/{counterId}/increment")
	public void incrementActorCounter(@PathVariable String counterId) {
		actorCounterService.increment(counterId);
	}

	/**
	 * Endpoint to get a counter value using actor-based locking.
	 *
	 * @param counterId The ID of the counter to get
	 * @return A Mono containing the current counter value
	 */
	@GetMapping("/actor/{counterId}")
	public Mono<Long> getActorCounter(@PathVariable String counterId) {
		return actorCounterService.getValue(counterId);
	}
}
