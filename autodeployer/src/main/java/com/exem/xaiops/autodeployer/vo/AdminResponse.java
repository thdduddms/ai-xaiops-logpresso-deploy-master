package com.exem.xaiops.autodeployer.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AdminResponse<T> {
    private boolean success;
    private long total;
    private String message;
    private T data;

    private AdminResponse() {}

    public AdminResponse(final boolean success, final String message) {
        this.success = success;
        this.message = message;
    }

    public AdminResponse(final T data, final long total) {
        this.success = true;
        this.data = data;
        this.total = total;
    }
}
