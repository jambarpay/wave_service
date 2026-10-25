package com.jambarpay.waveservice.domain.exception;

public class DomainException extends RuntimeException {
    private final String code;
    private final String providerCode;

    public DomainException(String message) {
        this("PAYMENT_REQUEST_REJECTED", message, null);
    }

    public DomainException(String code, String message) {
        this(code, message, null);
    }

    public DomainException(String code, String message, String providerCode) {
        super(message);
        this.code = code;
        this.providerCode = providerCode;
    }

    public String getCode() { return code; }
    public String getProviderCode() { return providerCode; }
}
