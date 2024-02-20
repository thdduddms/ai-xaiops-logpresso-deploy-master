package com.exem.xaiops.autodeployer.exceptions.impl;

import com.exem.xaiops.autodeployer.exceptions.ErrorCode;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
public class LPConnectionException extends CustomException {
    private Exception exception;

    /**
     * 로그프레소와 서버 연결을 끊던 중 발생한 예외의 경우 사용합니다.
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     */
    public LPConnectionException(final ErrorCode errorCode, final String detail) {
        super(errorCode, detail);
    }

    /**
     * 로그프레소와 서버 연결을 하던 중 발생한 예외의 경우 로그프레소 정보와 자바 표준 예외 메세지 등을 보여줍니다.
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     * @param connectLP 예외가 발생한 로그프레소 정보 (source/target)
     * @param exception 자바 표준 예외의 메세지
     */
    public LPConnectionException(final ErrorCode errorCode, final String detail, final String connectLP, final Exception exception) {
        super(errorCode, detail, connectLP, exception);
        this.exception=exception;
    }
    public Exception getException() {
        return exception;
    }
}