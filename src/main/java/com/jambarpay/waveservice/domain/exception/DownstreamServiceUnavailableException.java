package com.jambarpay.waveservice.domain.exception;

public class DownstreamServiceUnavailableException extends RuntimeException {

    public DownstreamServiceUnavailableException(String message) {
        super(message);
    }
}
