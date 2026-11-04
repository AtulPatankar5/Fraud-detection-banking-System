package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    /**
     * SAGA Step 1: Initiate Transfer
     * Deduct from sender via feign client
     * saves transaction as processing
     * publish event to kafka for fraud check
     * returns
     *
     * @param transferRequest
     * @return
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest transferRequest) {

        log.info("SAGA Starts - Transfer from {} -> {} and  amount {}",
                transferRequest.getSenderAccountNumber(),
                transferRequest.getReceiverAccountNumber(),
                transferRequest.getAmount());

        //SAGA Step1: Deduct from Sender
        accountServiceClient.deductBalance(transferRequest.getAmount(), transferRequest.getSenderAccountNumber());

        // Save transaction as processing
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(transferRequest.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(transferRequest.getReceiverAccountNumber());
        transaction.setAmount(transferRequest.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(transferRequest.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as Processing , {}", savedTransaction.getId());


//       SAGA Step 2:  publish event to kafka for fraud check
        TransactionInitiatedEvent initiatedEvent = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), initiatedEvent);

        log.info("SAGA Step 2: TransactionEvent Published ");
        return mapToResponse(savedTransaction);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse transactionResponse = new TransactionResponse();
        transactionResponse.setId(transaction.getId());
        transactionResponse.setDescription(transaction.getDescription());
        transactionResponse.setAmount(transaction.getAmount());
        transactionResponse.setSenderAccountNumber(transaction.getSenderAccountNumber());
        transactionResponse.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        transactionResponse.setStatus(transaction.getStatus());
        transactionResponse.setType(transaction.getType());
        transactionResponse.setReferenceNumber(transaction.getReferenceNumber());
        transactionResponse.setCompletedAt(transaction.getCompletedAt());
        transactionResponse.setCreationAt(transaction.getCreationAt());
        transactionResponse.setFailureReason(transaction.getFailureReason());

        return transactionResponse;

    }


    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId).orElseThrow(
                () -> new RuntimeException("No transaction found with id " + transactionId)));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.
                findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse verifyOtp(String transactionId, String otp) {

        log.info("Verify OTP request for transaction {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId).
                orElseThrow(() -> new RuntimeException("No transaction found with id " + transactionId));

        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        // OTP Expired
        if (storedOtp == null) {
            log.warn("No OTP found or OTP expired for transaction {}", transactionId);
            compensateTransaction(transaction, "OTP expired - Transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        // OTP Wrong
        if (!storedOtp.equals(otp)) {
            log.warn("Wrong OTP - blocking account and refunding {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                    "Wrong OTP entered - transaction cancelled " +
                            "Account blocked for security");
            return mapToResponse(transaction);
        }
        // OTP correct and complete transaction
        log.info("OTP verified - completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);

        completeTransaction(transaction);

        return mapToResponse(transaction);
    }

    private void completeTransaction(Transaction transaction) {

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent();
        completedEvent.setTransactionId(transaction.getId());
        completedEvent.setAmount(transaction.getAmount());
        completedEvent.setDescription(transaction.getDescription());
        completedEvent.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        completedEvent.setSenderAccountNumber(transaction.getSenderAccountNumber());

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);


        log.info("SAGA Completed -  Transaction {} completed", transaction.getId());


    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {

        //PUBLISH fraud.detected --> Accoutn service will block account
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        // send to account service to block the user
        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);

        log.warn("fraud.detected Published. Blocking account for account {}. Please contact your bank. ",
                transaction.getSenderAccountNumber());

        // SAGA Compensation and refund sender
        compensateTransaction(transaction, reason);


    }

    private void compensateTransaction(Transaction transaction, String reason) {

        log.warn("SAGA COMPENSATE. Refunding : {} and amount {}",
                transaction.getSenderAccountNumber(), transaction.getAmount());

        // FEIGN Client: call account service microservice to credit back balance for refund-
        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(), transaction.getAmount());

        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason +
                "- SAGA Compensation executed, amount refunded at  " + LocalDateTime.now());

        transactionRepository.save(transaction);

        // PUBLISH REFUND EVENT -- Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("refundAmount", transaction.getAmount());
        refundEvent.put("reason", reason);

        // send to notification service to notify user for refund
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA Compensation completed - {} refunded to {} ", transaction.getAmount(), transaction.getSenderAccountNumber());

    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).
                orElseThrow(() -> new RuntimeException("No transaction found with id " + transactionId));

        //Idempotency- ensure that transaction is not sent to receiver or sender twice.
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Transaction  {} not processing. Skipping ", transactionId);
            return;
        }

        completeTransaction(transaction);


    }
}
