package com.exem.xaiops.autodeployer.exceptions.impl;

import com.exem.xaiops.autodeployer.exceptions.ErrorCode;

public class NoObjectFoundException extends CustomException {
    private String missingObject;

    /**
     * 한 개 이상의 대상을 찾으려고 하였으나 아무 결과가 없을 경우 사용합니다.
     * 찾으려던 대상이 여러개였으므로 특정하지 않고 어떤 Logpresso에 찾으려던 대상이 하나도 없었는지 보여줍니다.
     * @param errorCode 에러 코드와 메세지 전달
     * @param detail 예외 발생 상황을 전달
     * @param connectLP 어떤 LP에서 발생했는지 전달
     */
    public NoObjectFoundException(final ErrorCode errorCode, final String detail, final String connectLP) {
        super(errorCode, detail, connectLP);
    }

    /**
     * 찾으려던 특정 대상이 존재하지 않을 경우 로그프레소 정보와 찾으려던 대상의 이름을 함께 보여줍니다.
     * @param errorCode 에러 코드와 메세지 전달
     * @param detail 예외 발생 상황을 메세지 형태로 전달
     * @param connectLP 어떤 LP에서 발생했는지 전달
     * @param missingObject (대상을 알 경우) 찾으려던 대상 전달
     */
    public NoObjectFoundException(final ErrorCode errorCode, final String detail, final String connectLP, final String missingObject) {
        super(errorCode, detail, connectLP);
        this.missingObject=missingObject;
    }
    public String getMissingObject() {
        return missingObject;
    }
}