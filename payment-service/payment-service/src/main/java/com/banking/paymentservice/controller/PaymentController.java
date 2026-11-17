package com.banking.paymentservice.controller;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.response.ApiResponse;
import com.banking.paymentservice.service.PaymentService;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentOrderResponse response = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Payment order created successfully",
                        response)

        );
    }

    //Razor pay webhook Endpoint

    /*
     * RAZORPAY WEBHOOK API
     *
     * This API is called automatically by Razorpay's server
     * whenever a subscribed payment event occurs.
     *
     * Example events:
     * - payment.captured
     * - payment.failed
     *
     * Razorpay sends an HTTP POST request to our configured
     * webhook URL.
     *
     * PARAMETERS:
     *
     * 1. rawBody:
     *    Contains the original JSON payload sent by Razorpay.
     *    We use byte[] to preserve the original request body
     *    for webhook signature verification.
     *
     * 2. signature:
     *    Received through X-Razorpay-Signature HTTP header.
     *    Used to verify that the webhook request is authentic.
     *    Verification uses RAZORPAY_WEBHOOK_SECRET.
     *
     * 3. eventId:
     *    Received through x-razorpay-event-id HTTP header.
     *    Uniquely identifies the webhook event.
     *    Used to prevent duplicate event processing.
     *
     * FLOW:
     * 1. Razorpay sends webhook request.
     * 2. Spring Boot receives the request.
     * 3. Controller passes the details to PaymentService.
     * 4. PaymentService verifies the webhook signature.
     * 5. Checks whether the event was already processed.
     * 6. Processes the payment event.
     * 7. Updates payment status in the database.
     * 8. Returns HTTP 200 after successful acceptance.
     *
     * IMPORTANT:
     * This API is called by Razorpay, not React frontend.
     */

    @PostMapping(
            value = "/webhook",
            consumes = "application/json"
    )
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader("x-razorpay-event-id") String eventId) {

        paymentService.handleWebhook(rawBody, signature, eventId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Webhook accepted for processing",
                        null
                )
        );
    }

}
