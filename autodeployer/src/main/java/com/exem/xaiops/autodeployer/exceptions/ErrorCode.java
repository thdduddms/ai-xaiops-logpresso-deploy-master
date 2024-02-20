package com.exem.xaiops.autodeployer.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 프로시저 관련 에러코드
    PROCEDURE_NOT_FOUND(404, "요청한 프로시저를 찾을 수 없습니다"),
    PROCEDURES_NOT_FOUND(404, "로그프레소에 프로시저가 존재하지 않습니다"),
    PROCEDURE_BACKUP_FAIL(500, "프로시저 단일 백업 과정에서 테이블 insert 중 예외가 발생하였습니다"),

    // 예약쿼리 관련 에러코드
    SCHEDULED_QUERY_NOT_FOUND(404, "요청한 예약쿼리를 찾을 수 없습니다"),
    SCHEDULED_QUERIES_NOT_FOUND(404, "로그프레소에 예약쿼리가 존재하지 않습니다"),

    // 스트림 쿼리 관련 에러코드
    STREAM_QUERY_NOT_FOUND(404, "요청한 스트림쿼리를 찾을 수 없습니다"),
    STREAM_QUERIES_NOT_FOUND(404, "로그프레소에 스트림 쿼리가 존재하지 않습니다"),

    // 공통 오브젝트 관련 에러코드
    OBJECT_NOT_FOUND(404, "요청한 오브젝트를 찾을 수 없습니다"),
    LP_TABLE_SCHEMA_EXCEPTION(500, "테이블 스키마를 가져오던 중 예외가 발생하였습니다"),

    // 로그프레소 관련 에러코드
    LP_ELEMENTS_EXCEPTION(500,"로그프레소 문법, 테이블/룩업 생성 여부, 권한 등의 내부 문제입니다"),
    LP_INSERT_EXCEPTION(500, "로그프레소에 insert 도중 예외가 발생했습니다"),
    DATA_NOT_INSERTED_EXCEPTION(404, "테이블에 저장된 건수가 없습니다. 확인이 필요합니다"),

    // 로그프레소 연결 관련 에러코드
    LP_CONNECTION_INFO_EXCEPTION(404, "로그프레소 접속 정보가 없습니다. YML 파일을 확인해주세요"),
    LP_CONNECT_EXCEPTION(500, "로그프레소 접속 정보나 프로세스를 확인해주세요");
    private final int status;
    private final String message;



}