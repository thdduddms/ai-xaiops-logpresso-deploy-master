package com.exem.xaiops.autodeployer.exceptions.impl;

import com.exem.xaiops.autodeployer.exceptions.ErrorCode;

public class LogpressoException extends CustomException {

    private Exception exception;

    /**
     * 로그프레소와 관련된 예외 중 Http Status와 메세지만 출력하고 싶을 때 사용합니다.
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     */
    public LogpressoException(final ErrorCode errorCode,final String detail) {
        super(errorCode, detail);
    }
    /**
     * 로그프레소와 관련된 예외 중 로그프레소 정보가 있을 경우 사용합니다.
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     * @param connectLP 예외가 발생한 로그프레소 정보 (source/target)
     */
    public LogpressoException(final ErrorCode errorCode, final String detail, final String connectLP) {
        super(errorCode, detail, connectLP);
    }
    /**
     * 로그프레소와 관련된 예외 중 로그프레소 정보가 있고, 자바 표준 예외를 유발한 경우 함께 사용할 수 있습니다.
     * @param errorCode ErrorCode에 정의된 에러코드 및 메세지
     * @param detail 발생한 예외에 대한 내용
     * @param connectLP 예외가 발생한 로그프레소 정보 (source/target)
     * @param e 자바 표준 예외의 메세지
     */
    public LogpressoException(final ErrorCode errorCode, final String detail, final String connectLP, final Exception e) {
        super(errorCode, detail, connectLP, e);
        this.exception=e;
    }
    public Exception getException() {
        return exception;
    }
}
