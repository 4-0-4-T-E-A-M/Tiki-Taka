package io.github.team404.tikitaka.global.exception;

public class JwtValidationException extends RuntimeException {

    public JwtValidationException(String message) {
        super(message);
    }
}
