package io.github.seonwkim.example.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    public ApiController(OrderService orderService,
                        PaymentService paymentService,
                        NotificationService notificationService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }

    @PostMapping("/orders")
    public Mono<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        log.info("Received order request for user: {}, amount: {}",
            request.userId, request.amount);

        return orderService.processOrder(request.userId, request.amount)
            .map(result -> new OrderResponse(
                result.orderId,
                result.status,
                result.message
            ));
    }

    @PostMapping("/payments")
    public Mono<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        log.info("Received payment request for order: {}, amount: {}",
            request.orderId, request.amount);

        return paymentService.processPayment(
                request.orderId,
                request.userId,
                request.amount,
                request.paymentMethod
            )
            .map(result -> new PaymentResponse(
                result.paymentId,
                result.status,
                result.transactionId,
                result.message
            ));
    }

    @PostMapping("/notifications")
    public Mono<NotificationResponse> sendNotification(@RequestBody NotificationRequest request) {
        log.info("Received notification request for user: {}, type: {}",
            request.userId, request.type);

        return notificationService.sendNotification(
                request.userId,
                request.type,
                request.message
            )
            .map(result -> new NotificationResponse(
                result.notificationId,
                result.status,
                result.message
            ));
    }

    @PostMapping("/checkout")
    public Mono<CheckoutResponse> checkout(@RequestBody CheckoutRequest request) {
        final String requestId = org.slf4j.MDC.get("requestId");
        final String userId = request.userId;

        log.info("Starting checkout for user: {}, amount: {}", userId, request.amount);

        return orderService.processOrder(userId, request.amount, requestId)
            .flatMap(orderResult -> {
                if ("SUCCESS".equals(orderResult.status)) {
                    return paymentService.processPayment(
                        orderResult.orderId,
                        userId,
                        request.amount,
                        request.paymentMethod,
                        requestId
                    ).map(paymentResult -> new Object[]{ orderResult, paymentResult });
                } else {
                    return Mono.error(new RuntimeException("Order processing failed: " + orderResult.message));
                }
            })
            .flatMap(results -> {
                OrderProcessorActor.OrderProcessed orderResult =
                    (OrderProcessorActor.OrderProcessed) results[0];
                PaymentProcessorActor.PaymentProcessed paymentResult =
                    (PaymentProcessorActor.PaymentProcessed) results[1];

                if ("SUCCESS".equals(paymentResult.status)) {
                    return notificationService.sendNotification(
                        userId,
                        "email",
                        "Your order " + orderResult.orderId + " has been processed successfully",
                        requestId
                    ).map(notifResult -> new CheckoutResponse(
                        "SUCCESS",
                        orderResult.orderId,
                        paymentResult.transactionId,
                        "Checkout completed successfully"
                    ));
                } else {
                    return Mono.error(new RuntimeException("Payment failed: " + paymentResult.message));
                }
            })
            .doOnSuccess(result -> log.info("Checkout completed successfully for order: {}", result.orderId))
            .doOnError(error -> log.error("Checkout failed", error));
    }

    // Request/Response DTOs
    public static class OrderRequest {
        public String userId;
        public double amount;
    }

    public static class OrderResponse {
        public String orderId;
        public String status;
        public String message;

        public OrderResponse(String orderId, String status, String message) {
            this.orderId = orderId;
            this.status = status;
            this.message = message;
        }
    }

    public static class PaymentRequest {
        public String orderId;
        public String userId;
        public double amount;
        public String paymentMethod;
    }

    public static class PaymentResponse {
        public String paymentId;
        public String status;
        public String transactionId;
        public String message;

        public PaymentResponse(String paymentId, String status, String transactionId, String message) {
            this.paymentId = paymentId;
            this.status = status;
            this.transactionId = transactionId;
            this.message = message;
        }
    }

    public static class NotificationRequest {
        public String userId;
        public String type;
        public String message;
    }

    public static class NotificationResponse {
        public String notificationId;
        public String status;
        public String message;

        public NotificationResponse(String notificationId, String status, String message) {
            this.notificationId = notificationId;
            this.status = status;
            this.message = message;
        }
    }

    public static class CheckoutRequest {
        public String userId;
        public double amount;
        public String paymentMethod;
    }

    public static class CheckoutResponse {
        public String status;
        public String orderId;
        public String transactionId;
        public String message;

        public CheckoutResponse(String status, String orderId, String transactionId, String message) {
            this.status = status;
            this.orderId = orderId;
            this.transactionId = transactionId;
            this.message = message;
        }
    }
}
