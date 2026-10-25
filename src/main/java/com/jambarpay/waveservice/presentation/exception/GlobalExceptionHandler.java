package com.jambarpay.waveservice.presentation.exception;

import com.jambarpay.waveservice.domain.exception.DomainException;
import com.jambarpay.waveservice.domain.exception.DownstreamServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.LinkedHashMap;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> handleDomainException(DomainException exception) {
        log.warn("Payment request rejected: code={}, providerCode={}", exception.getCode(), exception.getProviderCode());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", exception.getCode());
        body.put("message", exception.getMessage());
        if (exception.getProviderCode() != null) body.put("providerCode", exception.getProviderCode());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(DownstreamServiceUnavailableException.class)
    public ResponseEntity<String> handleDownstreamException(
            DownstreamServiceUnavailableException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Payment provider is currently unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException exception) {
        log.warn("Payment request rejected: code=PAYMENT_REQUEST_INVALID");
        return ResponseEntity.badRequest().body(Map.of("code", "PAYMENT_REQUEST_INVALID", "message", "La demande de paiement est invalide."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpectedException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected payment error occurred");
    }
}
