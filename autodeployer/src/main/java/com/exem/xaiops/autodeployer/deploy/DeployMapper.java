package com.exem.xaiops.autodeployer.deploy;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.exem.xaiops.autodeployer.Constant.*;

@Getter
@AllArgsConstructor
public enum DeployMapper {
    PROCEDURES(
            PROCEDURE
    ),
    SCHEDULES(
            SCHEDULED_QUERY
    ),
    STREAMS(
            STREAM_QUERY
    ),
    LOGGERS(
            LOCAL_LOGGER
    );
    private final String id;

    public static DeployMapper find(final String objectType) {
        return Arrays.stream(DeployMapper.values())
                .filter(type -> type.id.equals(objectType))
                .findFirst()
                .orElseThrow( () -> new RuntimeException(String.format("API Fail : [object type] %s - 잘못된 오브젝트 타입입니다.  ", objectType)));
    }

    public static List<DeployMapper> get() {
        return Arrays.stream(DeployMapper.values()).collect(Collectors.toList());
    }
}
