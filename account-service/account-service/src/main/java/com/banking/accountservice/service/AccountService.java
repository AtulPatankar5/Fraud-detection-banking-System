package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.exception.customExceptions.AccountAlreadyPresent;
import com.banking.accountservice.exception.customExceptions.AccountNotActive;
import com.banking.accountservice.exception.customExceptions.AccountNotFoundException;
import com.banking.accountservice.exception.customExceptions.InsufficientBalanceException;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    private static SecureRandom random = new SecureRandom();

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account request {}", request.getEmail());
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new AccountAlreadyPresent("Account already exists for email " + request.getEmail());
        }

        Account account = new Account();
        account.setEmail(request.getEmail());
        account.setAccountHolderName(request.getAccountHolderName());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getInitialDeposit());
        account.setPhone(request.getPhone());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(request.getAccountType() == AccountType.SAVINGS ? new BigDecimal("100000") : new BigDecimal("500000"));
        Account savedAccount = accountRepository.save(account);
        log.info("Account {} created : ", savedAccount.getAccountNumber());

        return mapToResponse(savedAccount);
    }


    // generate Unique 12 digit account number
    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = random.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);

        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private AccountResponse mapToResponse(Account savedAccount) {

        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(savedAccount.getId());
        accountResponse.setAccountNumber(savedAccount.getAccountNumber());
        accountResponse.setEmail(savedAccount.getEmail());
        accountResponse.setAccountHolderName(savedAccount.getAccountHolderName());
        accountResponse.setAccountType(savedAccount.getAccountType());
        accountResponse.setBalance(savedAccount.getBalance());
        accountResponse.setPhone(savedAccount.getPhone());
        accountResponse.setAccountStatus(savedAccount.getAccountStatus());
        accountResponse.setDailyTransactionLimit(savedAccount.getDailyTransactionLimit());
        accountResponse.setCreatedAt(savedAccount.getCreatedAt());

        return accountResponse;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByAccountNumber(String accountNumber) {
        Account account = getAccountOrThrow(accountNumber);
        return mapToResponse(account);
    }

    /**
     * Get account by AccountNumber
     *
     * @param accountNumber
     * @return
     */
    @Transactional(readOnly = true)
    public BigDecimal getbalance(String accountNumber) {
        Account account = getAccountOrThrow(accountNumber);
        return account.getBalance();
    }


    /**
     * Block Account - called by Fraud Detection Service via kafka
     *
     * @param accountNumber
     */
    @Transactional
    public void blockAccount(String accountNumber) {
        log.info("Blocking account for account number {}", accountNumber);
        Account account = getAccountOrThrow(accountNumber);
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account {} blocked", accountNumber);
    }

    /**
     * Called by Transaction service.
     * Deduct balance for sender Account
     *
     * @param accountNumber
     * @param amount
     */
    @Transactional
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting balance for account number {}", accountNumber);
        Account account = getAccountOrThrow(accountNumber);
        if (!account.getAccountStatus().equals(AccountStatus.ACTIVE)) {
            throw new AccountNotActive("Account status is not ACTIVE");
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for account number");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance Updated. new Balance {} ", account.getBalance());
    }

    /**
     *
     * @param accountNumber
     * @param amount
     */
    @Transactional
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Credit initiated for account number {} and amount  {}", accountNumber, amount);
        Account account = getAccountOrThrow(accountNumber);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Credit initiated. new Balance {} ", account.getBalance());

    }

    private Account getAccountOrThrow(String accountNumber) {

        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found with account number: " + accountNumber
                        )
                );
    }
}
