package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.AccountService;
import com.portfolio.tracker.service.AccountService.Account;
import com.portfolio.tracker.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The signed-in user's account: the starting bankroll (S₀) and cash, and deleting the account after an
 * emailed confirmation code. Account errors come back as {"error", "code"} via the exception handler.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final UserService userService;

    public AccountController(AccountService accountService, UserService userService) {
        this.accountService = accountService;
        this.userService = userService;
    }

    @GetMapping
    public Map<String, Object> account(Authentication authentication) {
        return toBody(accountService.account(currentUser(authentication)));
    }

    /** Body: {"amount": 10000, "resetPortfolio": false}. S₀ can be set once. */
    @PutMapping("/bankroll")
    public ResponseEntity<?> setBankroll(@RequestBody Map<String, Object> request, Authentication authentication) {
        BigDecimal amount;
        try {
            Object raw = request.get("amount");
            amount = raw == null ? null : new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "The bankroll must be a number.", "code", "INVALID_BANKROLL"));
        }
        boolean reset = Boolean.TRUE.equals(request.get("resetPortfolio"));
        return ResponseEntity.ok(toBody(accountService.setBankroll(currentUser(authentication), amount, reset)));
    }

    /** Body: {"password": "..."}. Emails a code that confirms the deletion. */
    @PostMapping("/delete/request")
    public ResponseEntity<?> requestDeletion(@RequestBody(required = false) Map<String, String> request,
                                             Authentication authentication) {
        User user = currentUser(authentication);
        boolean sent = accountService.requestDeletion(user, request == null ? null : request.get("password"));
        if (!sent) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error",
                    "The confirmation email couldn't be sent. Please try again later.", "code", "EMAIL_SEND_FAILED"));
        }
        return ResponseEntity.accepted().body(Map.of("message", "We emailed a confirmation code to " + maskEmail(user.getEmail()) + ".",
                "expiresInSeconds", accountService.getDeletionCodeTtlSeconds()));
    }

    /** Body: {"code": "123456"}. Permanently deletes the account, its holdings, trades and sessions. */
    @PostMapping("/delete/confirm")
    public ResponseEntity<Void> confirmDeletion(@RequestBody(required = false) Map<String, String> request,
                                                Authentication authentication) {
        accountService.confirmDeletion(currentUser(authentication), request == null ? null : request.get("code"));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName());
    }

    private static Map<String, Object> toBody(Account a) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", a.username());
        body.put("email", a.email());
        body.put("initialBankroll", a.initialBankroll());
        body.put("bankrollSetAt", a.bankrollSetAt() == null ? null : a.bankrollSetAt().toString());
        body.put("bankrollRequired", a.bankrollRequired());
        body.put("cash", a.cash());
        body.put("netInvested", a.netInvested());
        body.put("minimumBankroll", a.minimumBankroll());
        body.put("hasPortfolio", a.hasPortfolio());
        return body;
    }

    /** j***@gmail.com: enough to recognise the address without showing it in full. */
    static String maskEmail(String email) {
        if (email == null) return "your email";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
