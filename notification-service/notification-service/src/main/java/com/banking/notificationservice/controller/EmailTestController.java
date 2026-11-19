
package com.banking.notificationservice.controller;

import com.banking.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class EmailTestController {

    private final EmailService emailService;

    @PostMapping("/test-email")
    public ResponseEntity<String> testEmail(
            @RequestParam String email) {

        emailService.sendEmail(
                email,
                "Banking Notification Test",
                "Congratulations! Your Gmail SMTP integration "
                        + "is working successfully."
        );

        return ResponseEntity.ok("Email sent successfully");
    }
}
