package com.exem.xaiops.autodeployer.exceptions.impl;

import com.exem.xaiops.autodeployer.exceptions.ErrorCode;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
public class CustomException extends RuntimeException {
    final private ErrorCode errorCode;
    private final String detail;
    private String connectLP;
    private Exception exception;

    /**
     *
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     */
    public CustomException(final ErrorCode errorCode, final String detail) {
        super(errorCode.getMessage());
        this.errorCode=errorCode;
        this.detail = detail;
    }

    /**
     *
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     * @param connectLP 예외가 발생한 로그프레소 정보 (source/target)
     */
    public CustomException(final ErrorCode errorCode, final String detail, final String connectLP) {
        super(errorCode.getMessage());
        this.errorCode=errorCode;
        this.detail=detail;
        this.connectLP = connectLP;
    }

    /**
     *
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     * @param connectLP 예외가 발생한 로그프레소 정보 (source/target)
     * @param exception 자바 표준 예외의 메세지
     */
    public CustomException(final ErrorCode errorCode, final String detail, final String connectLP, final Exception exception) {
        super(errorCode.getMessage());
        this.errorCode=errorCode;
        this.detail=detail;
        this.connectLP = connectLP;
        this.exception=exception;
    }
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    public String getConnectLP() { return connectLP; }
    public String getDetail() { return detail;}
    public Exception getException() { return exception; }
}