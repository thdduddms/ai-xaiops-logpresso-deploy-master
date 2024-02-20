package com.exem.xaiops.autodeployer.exceptions;

import com.exem.xaiops.autodeployer.exceptions.impl.CustomException;
import com.exem.xaiops.autodeployer.exceptions.impl.LPConnectionException;
import com.exem.xaiops.autodeployer.exceptions.impl.LogpressoException;
import com.exem.xaiops.autodeployer.exceptions.impl.NoObjectFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

@Log4j2
@RestControllerAdvice(basePackages = "com.exem.xaiops.autodeployer")
public class ControllerExceptionHandler {

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(final CustomException ce) {
        log.error("custom exception {}", ce.getMessage());
        final ErrorCode errorCode = ce.getErrorCode();
        final ErrorResponse response = ErrorResponse.create().status(HttpStatus.NOT_FOUND.value()).message(ce.toString());
        final HttpStatus status = Optional.ofNullable(HttpStatus.resolve(errorCode.getStatus()))
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, status);
    }
    @ExceptionHandler(NoObjectFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNoObjectFoundException(final NoObjectFoundException nofe) {
        final ErrorCode errorCode = nofe.getErrorCode();
        final String detail = nofe.getDetail();
        final String missingObject = StringUtils.hasText(nofe.getMissingObject()) ? nofe.getMissingObject() : "전체 대상";
        final String message = String.format("API Fail (NoObjectFoundException) : [%s] %s [%s] %s", nofe.getConnectLP(), nofe.getMessage(), missingObject, detail);
        log.error(message);

        final ErrorResponse response = ErrorResponse.create().status(HttpStatus.NOT_FOUND.value()).message(message);
        final HttpStatus status = Optional.ofNullable(HttpStatus.resolve(errorCode.getStatus()))
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, status);
    }
    @ExceptionHandler(LPConnectionException.class)
    protected ResponseEntity<ErrorResponse> handleLPConnectionException(final LPConnectionException e) {
        final ErrorCode errorCode = e.getErrorCode();
        final String message = String.format("API Fail (LPConnectionException) : [%s] 로그프레소 연결에 실패하였습니다. [%s] - %s",
                e.getConnectLP(), e.getMessage(), e.getException().getMessage());
        log.error(message);

        final ErrorResponse response = ErrorResponse.create().status(errorCode.getStatus()).message(message);
        final HttpStatus status = Optional.ofNullable(HttpStatus.resolve(errorCode.getStatus()))
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, status);
    }
    @ExceptionHandler(LogpressoException.class)
    protected ResponseEntity<ErrorResponse> handleLogpressoException(final LogpressoException e) {
        final ErrorCode errorCode = e.getErrorCode();
        final String detail = e.getDetail();
        final String exception = StringUtils.hasText(e.getException().getMessage()) ? e.getException().getMessage() : "";
        final String message = String.format("API Fail (LogpressoExcpetion) : [%s] 로그프레소에서 문제가 발생하였습니다. - %s - %s -%s",
                e.getConnectLP(), e.getMessage(), detail, exception);
        log.error(message);

        final ErrorResponse response = ErrorResponse.create().status(500).message(message);
        final HttpStatus status = Optional.ofNullable(HttpStatus.resolve(errorCode.getStatus()))
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, status);
    }
}