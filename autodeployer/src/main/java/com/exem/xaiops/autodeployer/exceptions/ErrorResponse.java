package com.exem.xaiops.autodeployer.exceptions;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ErrorResponse {
    private LocalDateTime time = LocalDateTime.now();
    private String message;
    private int status;
    private String name;

    public ErrorResponse(){ }

    static public ErrorResponse create() {
        return new ErrorResponse();
    }

    public ErrorResponse status(int status) {
        this.status=status;
        return this;
    }
    public ErrorResponse message(String message) {
        this.message=message;
        return this;
    }

    public ErrorResponse name(String name) {
        this.name= name;
        return this;
    }
}
