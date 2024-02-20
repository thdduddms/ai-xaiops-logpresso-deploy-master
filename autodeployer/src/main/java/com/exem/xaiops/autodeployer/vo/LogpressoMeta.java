package com.exem.xaiops.autodeployer.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper=true)
public class LogpressoMeta {
    @ApiModelProperty(value="배포할 객체 유형", position = 0, example = "procedure", required = true)
    private String object_type;

    @Getter
    public static class Backup extends LogpressoMeta {
        @ApiModelProperty(value="배포 원본 객체 이름", position = 1, example = "server_dm_metric_list", required = true)
        private String object_name;
    }
    @Getter
    public static class Pull extends LogpressoMeta {
        @ApiModelProperty(value="배포 원본 객체 이름", position = 1, example = "server_dm_metric_list", required = true)
        private String object_name;
        @ApiModelProperty(value="배포 대상 객체 신규 이름 (생략가능)", position = 2, example = "server_dm_metric_list_new")
        private String new_object_name;
    }

    @Getter
    public static class Restore extends LogpressoMeta {
        @ApiModelProperty(value="배포 원본 객체 이름", position = 1, example = "server_dm_metric_list", required = true)
        private String object_name;
        @ApiModelProperty(value="조회 시작 시점", position = 1, example = "2023-03-09 14:00:00", required = true)
        private String from;
        @ApiModelProperty(value="조회 종료 시점", position = 1, example = "2023-03-13 18:00:00", required = true)
        private String to;
    }
}
