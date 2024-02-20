package com.exem.xaiops.autodeployer.vo;

import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
@ToString(callSuper=true)
public class DeployMultiMeta {
    @ApiModelProperty(value="배포 대상 프로시저 이름(복수)", example = "[\"server_instance_view_db\", \"server_instance_view_was\"]", position = 1)
    private List<String> procedureList;

    @ApiModelProperty(value="배포 대상 예약쿼리 이름(복수)", example = "[\"apdex_stat_instance\", \"apdex_stat_infra\"]", position = 2)
    private List<String> scheduleList;

    @ApiModelProperty(value="배포 대상 스트림쿼리 이름(복수)", example = "[\"exem_aiops_anls_log_os\", \"exem_aiops_anls_log_db\"]", position = 3)
    private List<String> streamList;

    @Getter
    public static class RestoreMulti extends DeployMultiMeta {
        @ApiModelProperty(value="조회 시작 시점", position = 4, example = "2023-03-09 14:00:00", required = true)
        private String from;
        @ApiModelProperty(value="조회 종료 시점", position = 5, example = "2023-03-13 18:00:00", required = true)
        private String to;

        public RestoreMulti(List<String> procedureList, List<String> scheduleList, List<String> streamList) {
            super(procedureList, scheduleList, streamList);
        }
    }

    @Getter
    public static class cllProcChangeList {
        @ApiModelProperty(value="조회 시작 시점", position = 1, example = "2023-03-09 14:00:00", required = true)
        private String from;
    }
    public List<String> getDeployList(final DeployMapper mapper) {
        List<String> list = new ArrayList<>();
        switch (mapper) {
            case PROCEDURES:
                list.addAll(procedureList);
                break;
            case SCHEDULES:
                list.addAll(scheduleList);
                break;
            case STREAMS:
                list.addAll(streamList);
                break;
        }
        return list;
    }
}
