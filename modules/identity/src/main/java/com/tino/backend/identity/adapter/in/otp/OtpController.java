package com.tino.backend.identity.adapter.in.otp;

import com.tino.backend.identity.application.model.OtpChallengeIssued;
import com.tino.backend.identity.application.model.OtpVerificationResult;
import com.tino.backend.identity.application.usecase.RequestOtp;
import com.tino.backend.identity.application.usecase.VerifyOtp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TINO-native pre-authentication endpoints; neither endpoint requires a bearer token. */
@RestController
@RequestMapping("/api/v1/auth/otp")
public class OtpController {
    private final RequestOtp requestOtp;
    private final VerifyOtp verifyOtp;

    public OtpController(RequestOtp requestOtp, VerifyOtp verifyOtp) {
        this.requestOtp = requestOtp;
        this.verifyOtp = verifyOtp;
    }

    @PostMapping("/challenges")
    @Transactional
    public ResponseEntity<OtpChallengeIssued> request(
            @RequestBody(required = false) Request request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            throw new com.tino.backend.identity.application.exception.OtpInvalidRequestException();
        }
        var origin = httpRequest == null ? null : httpRequest.getRemoteAddr();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestOtp.execute(request.phone(), origin));
    }

    @PostMapping("/challenges/{challengeId}/verify")
    @Transactional
    public OtpVerificationResult verify(
            @PathVariable UUID challengeId,
            @RequestBody(required = false) VerifyRequest request) {
        if (request == null) {
            throw new com.tino.backend.identity.application.exception.OtpInvalidRequestException();
        }
        return verifyOtp.execute(challengeId, request.code());
    }

    public record Request(String phone) {}

    public record VerifyRequest(String code) {}
}
