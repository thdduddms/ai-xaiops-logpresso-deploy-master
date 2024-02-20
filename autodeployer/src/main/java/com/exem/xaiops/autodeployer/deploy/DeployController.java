package com.exem.xaiops.autodeployer.deploy;

import com.exem.xaiops.autodeployer.vo.AdminResponse;
import com.exem.xaiops.autodeployer.vo.DeployExecutionResult;
import com.exem.xaiops.autodeployer.vo.DeployMultiMeta;
import com.exem.xaiops.autodeployer.vo.LogpressoMeta;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.ApiOperation;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import java.util.*;
import java.util.stream.Collectors;

import static com.exem.xaiops.autodeployer.Constant.*;

@RestController
@Log4j2
public class DeployController {

    private final Map<DeployMapper, Deploy<?>> deploy = new HashMap<>();

    public DeployController(final List<Deploy<?>> deployBeans) {
        deployBeans.forEach(bean -> deploy.put(bean.getMapper(), bean));
    }

    @ApiOperation(value = "Source 로그프레소로부터 데이터를 받아와서 Target LP에 생성 (단건)", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리), stream(스트림쿼리), logger(로컬 수집설정) 유형 사용 가능")
    @PostMapping("/targetLP/pull/one")
    public AdminResponse<?> pullOne(@RequestBody final LogpressoMeta.Pull deployMeta, HttpServletRequest request) throws IOException {
        final String objectType = deployMeta.getObject_type().trim();
        final String pullOneFromSource = deployMeta.getObject_name().trim();
        String pullOneToTargetCreate = deployMeta.getNew_object_name().trim();

        if (pullOneToTargetCreate.equals("")) {
            pullOneToTargetCreate = pullOneFromSource;
        }

        // API 요청시 작성한 id(procedure, scheduled 등)으로 Deploy 를 상속한 클래스의 run 메서드를 사용
        final boolean created = deploy.get(DeployMapper.find(objectType)).fetchAndDeploy(pullOneFromSource, pullOneToTargetCreate);
        deploy.get(DeployMapper.find(objectType)).deployHistoryInsert(objectType, pullOneFromSource, getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
        final String message = String.format("[%s] %s 생성되었습니다. ", objectType, pullOneToTargetCreate);
        return new AdminResponse<>(created, message);

    }

    @ApiOperation(value = "Source LP의 정보를 받아와서 Target LP에 생성 (전체)", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리), stream(스트림쿼리) 유형 사용 가능")
    @PostMapping("/targetLP/pull/all")
    public AdminResponse<?> pullAll(@RequestBody final LogpressoMeta deployMeta, HttpServletRequest request) throws IOException {
        final long start = System.currentTimeMillis();
        final String objectType = deployMeta.getObject_type().trim();
        DeployExecutionResult results = deploy.get(DeployMapper.find(objectType)).fetchAndDeployBatch();

        final List<String> failedList = results.getFailedList();
        final List<String> successfulList = results.getSuccessfulList();

        final long end = System.currentTimeMillis();

        if (results.getFailedList().size() != 0) {
            deploy.get(DeployMapper.find(objectType)).deployHistoryInsert(objectType, "전체 대상", getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            log.info("아래 대상들이 성공적으로 배포되었습니다 - {}ms", end - start);
            log.info("\tㄴ배포 성공한 {} 리스트 : {}", objectType, successfulList);
            log.warn("Caution : 아래 {}건의 오브젝트들이 배포에 실패하였습니다", failedList.size());
            log.warn("\tㄴ배포 실패한 {} 리스트 : {}", objectType, failedList);
            return new AdminResponse<>(false, String.format("Failed pull count %d - 실패한 리스트 %s", failedList.size(), failedList));
        } else {
            log.info("모든 대상들이 성공적으로 배포되었습니다 - {}ms : {}", end - start, successfulList);
            return new AdminResponse<>(successfulList, successfulList.size());
        }
    }

    @ApiOperation(value = "Target LP의 정보를 받아와서 테이블에 백업 (단건)", notes = "\tobject_typ (아래 항목 지원 가능 : 백업되는 속성)\n\t　ㄴ procedure(프로시저) : (*) object_type, name, owner, query_string, parameters // grant_groups, grants, description\n\t　ㄴ schedule(예약쿼리) : (*) object_type, title, cron_schedule, owner, query // use_alert, alert_query, suppress_interval, mail_profile, mail_from, mail_to, mail_subject\n\t　ㄴ stream(스트림쿼리) : (*) object_type, name, interval, source_type, sources, owner, query, is_enabled // description, is_async(수동확인)")
    @PostMapping("/targetLP/backup/one")
    public AdminResponse backupOne(@RequestBody final LogpressoMeta.Backup deployMeta, HttpServletRequest request) throws IOException {
        final String findToObjectType = deployMeta.getObject_type().trim();
        final String findToObjectName = deployMeta.getObject_name().trim();
        boolean backup = false;
        String return_msg = String.format("Backup(one) Fail : [%s] %s - Target 로그프레소에 백업(테이블) 실패", findToObjectType, findToObjectName); //default : 실패 message

        backup = deploy.get(DeployMapper.find(findToObjectType)).findAndBackup(findToObjectName);

        if (backup) {
            deploy.get(DeployMapper.find(findToObjectType)).deployHistoryInsert(findToObjectType, findToObjectName, getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            log.info("Backup(one) Finish : [{}] {} 백업 되었습니다.", findToObjectType, findToObjectName);
            return_msg = String.format("Backup(one) Finish : [%s] %s", findToObjectType, findToObjectName);
        } else {
//                log.error("Backup(one) Fail : [{}] {} - 대상 로그프레소의 백업(테이블) 과정에서 오류가 발생하였습니다.", findToObjectType, findToObjectName);
        }

        return new AdminResponse(backup, return_msg);
    }

    @ApiOperation(value = "Target LP의 정보를 받아와서 테이블에 백업 (복수)", notes = "\tobject_typ (아래 항목 지원 가능 : 백업되는 속성)\n\t　ㄴ procedure(프로시저) : (*) object_type, name, owner, query_string, parameters // grant_groups, grants, description\n\t　ㄴ schedule(예약쿼리) : (*) object_type, title, cron_schedule, owner, query // use_alert, alert_query, suppress_interval, mail_profile, mail_from, mail_to, mail_subject\n\t　ㄴ stream(스트림쿼리) : (*) object_type, name, interval, source_type, sources, owner, query, is_enabled // description, is_async(수동확인)")
    @PostMapping("/targetLP/backup/multi")
    public AdminResponse backupMulti(@RequestBody final DeployMultiMeta deployMultiMeta, HttpServletRequest request) {
        final List<String> findToProcedureList = deployMultiMeta.getProcedureList();
        final List<String> findToScheduleList = deployMultiMeta.getScheduleList();
        final List<String> findToStreamList = deployMultiMeta.getStreamList();
        int errorCountProcedure = 0;
        int errorCountSchedule = 0;
        int errorCountStream = 0;
        HashMap<String, List<String>> errorList = new HashMap<String, List<String>>();
        String return_msg = "";

        try {
            List findToName = new ArrayList();
            if (null != findToProcedureList && 0 < findToProcedureList.size()) {
                for (String findToProcedure : findToProcedureList) {
                    if (deploy.get(DeployMapper.find(PROCEDURE)).findAndBackup(findToProcedure)) {
                    } else {
                        findToName.add(findToProcedure);
                        ++errorCountProcedure;
                    }
                }
                errorList.put(PROCEDURE, findToName);
            }
            findToName = new ArrayList();
            if (null != findToScheduleList && 0 < findToScheduleList.size()) {
                for (String findToSchedule : findToScheduleList) {
                    if (deploy.get(DeployMapper.find(SCHEDULED_QUERY)).findAndBackup(findToSchedule)) {
                    } else {
                        findToName.add(findToSchedule);
                        ++errorCountSchedule;
                    }
                }
                errorList.put(SCHEDULED_QUERY, findToName);
            }
            findToName = new ArrayList();
            if (null != findToStreamList && 0 < findToStreamList.size()) {
                for (String findToStream : findToStreamList) {
                    if (deploy.get(DeployMapper.find(STREAM_QUERY)).findAndBackup(findToStream)) {
                    } else {
                        findToName.add(findToStream);
                        ++errorCountStream;
                    }
                }
                errorList.put(STREAM_QUERY, findToName);
            }
            if (0 < errorCountProcedure || 0 < errorCountSchedule || 0 < errorCountStream) {
                log.warn("Backup(multi) Exception List : {}", errorList);
            }

            //history 테이블 insert
            if(findToProcedureList.size() > 0){
                deploy.get(DeployMapper.find(PROCEDURE)).deployHistoryInsert(PROCEDURE, findToProcedureList.stream().filter(x->!errorList.get(PROCEDURE).contains(x)).collect(Collectors.toList()), getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            }
            if (findToScheduleList.size() > 0){
                deploy.get(DeployMapper.find(SCHEDULED_QUERY)).deployHistoryInsert(SCHEDULED_QUERY, findToScheduleList.stream().filter(x->!errorList.get(SCHEDULED_QUERY).contains(x)).collect(Collectors.toList()), getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            }
            if (findToStreamList.size() > 0){
                deploy.get(DeployMapper.find(STREAM_QUERY)).deployHistoryInsert(STREAM_QUERY, findToStreamList.stream().filter(x->!errorList.get(STREAM_QUERY).contains(x)).collect(Collectors.toList()), getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            }

            return_msg = String.format("Backup(multi) Finish : [PROCEDURE] 총 %s건(성공 %s건/예외 %s건) [SCHEDULED_QUERY] 총 %s건(성공 %s건/예외 %s건) [STREAM_QUERY] 총 %s건(성공 %s건/예외 %s건) 처리되었습니다.", findToProcedureList.size(), findToProcedureList.size() - errorCountProcedure, errorCountProcedure, findToScheduleList.size(), findToScheduleList.size() - errorCountSchedule, errorCountSchedule, findToStreamList.size(), findToStreamList.size() - errorCountStream, errorCountStream);
            log.warn(return_msg);
        } catch (Exception e) {
            return_msg = String.format("Backup(multi) Fail : [PROCEDURE] %s 건 [SCHEDULED_QUERY] %s 건 [STREAM_QUERY] %s 건 - Target 로그프레소에 백업(테이블) 과정이 실패되었습니다.", findToProcedureList.size(), findToScheduleList.size(), findToStreamList.size());
            log.error(return_msg);
            log.error(e);
        }
        return new AdminResponse(errorCountProcedure == 0 && errorCountSchedule == 0 && errorCountStream == 0, return_msg);
    }

    @ApiOperation(value = "Target LP의 정보를 받아와서 테이블에 백업 (전체)", notes = "\tobject_type (지원 겍체 : 백업되는 속성)\n\t　ㄴ procedure(프로시저) : (*) object_type, name, owner, query_string, parameters // grant_groups, grants, description\n\t　ㄴ schedule(예약쿼리) : (*) object_type, title, cron_schedule, owner, query // use_alert, alert_query, suppress_interval, mail_profile, mail_from, mail_to, mail_subject\n\t　ㄴ stream(스트림쿼리) : (*) object_type, name, interval, source_type, sources, owner, query, is_enabled // description, is_async(수동확인)")
    @PostMapping("/targetLP/backup/all")
    public AdminResponse<Object> backupAll(@RequestBody final LogpressoMeta deployMeta, HttpServletRequest request) throws IOException {
        final String findToObjectType = deployMeta.getObject_type().trim();
        boolean backup = false;
        String return_msg = "";

        backup = deploy.get(DeployMapper.find(findToObjectType)).findAndBackupAll();

        if (backup) {
            deploy.get(DeployMapper.find(findToObjectType)).deployHistoryInsert(findToObjectType, "전체 대상", getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            return_msg = String.format("Backup Finish : [%s] 일괄 백업 되었습니다.", findToObjectType);
            log.info(return_msg);
        } else {
/*
                return_msg = String.format("Backup(all) Fail : [%s] - Target 로그프레소의 백업(테이블) 과정에서 예외가 발생하였습니다.", findToObjectType);
                log.error(return_msg);
*/
        }
        /*catch (Exception e) {
            return_msg = String.format("Backup(all) Fail : [%s] - Target 로그프레소의 백업(테이블) 과정이 실패하였습니다.", findToObjectType);
            log.error(return_msg);
            log.error(e);
        }*/
        return new AdminResponse<>(backup, return_msg);
    }

    @ApiOperation(value = "Target LP의 백업된 데이터를 이용한 복원 (단건)", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리), stream(스트림쿼리) 유형 사용 가능")
    @PostMapping("/targetLP/restore/one")
    public AdminResponse restoreOne(@RequestBody final LogpressoMeta.Restore deployMeta, HttpServletRequest request) throws IOException {
        final String objectType = deployMeta.getObject_type().trim();
        final String restoreOneFromTarget = deployMeta.getObject_name().trim();
        DateTime from = summaryFormatter.parseDateTime(deployMeta.getFrom());
        DateTime to = summaryFormatter.parseDateTime(deployMeta.getTo());

        String result = deploy.get(DeployMapper.find(objectType)).restoreOneFromTargetLP(restoreOneFromTarget, from, to);
        if(result == null) {
            log.error("Restore Fail : [{}] {} - 원복할 데이터가 없습니다.", objectType, restoreOneFromTarget);
            return new AdminResponse<>(false, String.format("Restore Fail : [%s] 원복할 데이터가 없습니다", objectType, restoreOneFromTarget));
        } else {
            deploy.get(DeployMapper.find(objectType)).deployHistoryInsert(objectType, restoreOneFromTarget, getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            log.info("Restore Finish : [{}] {} 원복 되었습니다.", objectType, restoreOneFromTarget);
            return new AdminResponse<>(true, String.format("Restore Success : [%s] %s", objectType, restoreOneFromTarget));
        }
    }

    @ApiOperation(value = "Target LP의 백업된 데이터를 이용한 복원 (복수)", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리), stream(스트림쿼리) 유형 사용 가능")
    @PostMapping("/targetLP/restore/multi")
    public AdminResponse<Object> restoreMulti(@RequestBody final DeployMultiMeta.RestoreMulti deployMultiMeta, HttpServletRequest request) {
        final long start = System.currentTimeMillis();
        final List<DeployMapper> listDeployMappers = DeployMapper.get();
        DateTime from = summaryFormatter.parseDateTime(deployMultiMeta.getFrom());
        DateTime to = summaryFormatter.parseDateTime(deployMultiMeta.getTo());

        // 오브젝트 타입별 배포 프로세스 실행
        final Map<String, DeployExecutionResult> results = new HashMap<>();
        listDeployMappers.stream().collect(Collectors.toMap(objectType -> objectType, deployMultiMeta::getDeployList))
            .forEach((objectType, name) -> {
                results.put(objectType.toString(), deploy.get(objectType).restoreMultiFromTargetLP(name, from, to));
            });

        //백업 테이블에 없어서 배포 실패한 리스트
        final Map<String, List<String>> nonExistent = new HashMap<>(listDeployMappers.stream()
                .collect(Collectors.toMap(Enum::toString, deployMultiMeta::getDeployList)));
        final int totalCount = nonExistent.values().stream().mapToInt(List::size).sum();

        results.forEach((objectType, names) -> {
            nonExistent.get(objectType).removeAll(names.getSuccessfulList());
            nonExistent.get(objectType).removeAll(names.getFailedList());
        });

        results.forEach((objectType, names) -> {
            String object_type=DeployMapper.valueOf(objectType).getId();
            try {
                deploy.get(DeployMapper.find(object_type)).deployHistoryInsert(object_type, names.getSuccessfulList(), getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // 배포 성공/실패 건수 확인
        final Map<String, List<String>> success = results.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getSuccessfulList()));
        final Map<String, List<String>> failed = results.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFailedList()));

        final int successCount = success.values().stream().mapToInt(List::size).sum();
        final int failedCount = failed.values().stream().mapToInt(List::size).sum();
        final int nonExistentCount = nonExistent.values().stream().mapToInt(List::size).sum();

        final long end = System.currentTimeMillis();

        if (failedCount + nonExistentCount > 0) {
            log.info("아래 {}건의 대상들이 성공적으로 원복되었습니다 [{}/{}] - {}ms", successCount, successCount, totalCount, end - start);
            log.info("\tㄴ배포 성공한 객체 목록 : {}", success);
            log.warn("Caution : 아래 {}건의 객체들이 원복에 실패하였습니다", failedCount);
            log.warn("\tㄴ배포 실패한 객체 목록 : {}", failed);
            log.warn("Caution : 아래 {}건의 객체들이 백업테이블에 존재하지 않아 원복에 실패하였습니다", nonExistentCount);
            log.warn("\tㄴ백업테이블에 없는 객체 목록 : {}", nonExistent);
            return new AdminResponse<>(false, String.format("Failed restore count %d - 실패한 리스트 %s", failedCount, failed));
        } else {
            log.info("모든 대상들이 성공적으로 원복되었습니다. ({}건) - {}ms : {}", successCount, end - start, results);
            return new AdminResponse<>(success, successCount);
        }    }

    @ApiOperation(value = "Source LP의 정보를 받아와서 Target LP에 배포 (복수)", notes = "\tobject_type (아래 항목 지원 가능 : 배포 가능한 속성)\n\t　ㄴ procedure(프로시저) : (*) object_type, name, owner, query_string, parameters // grant_groups, grants, description\n\t　ㄴ schedule(예약쿼리) : (*) object_type, title, cron_schedule, owner, query // use_alert, alert_query, suppress_interval, mail_profile, mail_from, mail_to, mail_subject\n\t　ㄴ stream(스트림쿼리) : (*) object_type, name, interval, source_type, sources, owner, query, is_enabled // description, is_async(수동확인)")
    @PostMapping("/targetLP/pull/multi")
    public AdminResponse<Object> pullMulti(@RequestBody final DeployMultiMeta deployMultiMeta, HttpServletRequest request) {
        final long start = System.currentTimeMillis();
        final List<DeployMapper> listDeployMappers = DeployMapper.get();

        // 오브젝트 타입별 배포 프로세스 실행
        final Map<String, DeployExecutionResult> results = new HashMap<>();
        listDeployMappers.stream().collect(Collectors.toMap(objectType -> objectType, deployMultiMeta::getDeployList))
                .forEach((objectType, name) -> {
                    name.sort(null);
                    results.put(objectType.toString(), deploy.get(objectType).fetchAndDeployMulti(name));
                });

        //소스 Logpresso에 없어서 배포 실패한 리스트
        final Map<String, List<String>> nonExistent = new HashMap<>(listDeployMappers.stream()
                .collect(Collectors.toMap(Enum::toString, deployMultiMeta::getDeployList)));
        final int totalCount = nonExistent.values().stream().mapToInt(List::size).sum();

        results.forEach((objectType, names) -> {
            nonExistent.get(objectType).removeAll(names.getSuccessfulList());
            nonExistent.get(objectType).removeAll(names.getFailedList());
        });

        results.forEach((objectType, names) -> {
            String object_type=DeployMapper.valueOf(objectType).getId();
            try {
                deploy.get(DeployMapper.find(object_type)).deployHistoryInsert(object_type, names.getSuccessfulList(), getRemoteIp(request).get("ip"), getRemoteIp(request).get("url"), true, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // 배포 성공/실패 건수 확인
        final Map<String, List<String>> success = results.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getSuccessfulList()));
        final Map<String, List<String>> failed = results.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFailedList()));

        final int successCount = success.values().stream().mapToInt(List::size).sum();
        final int failedCount = failed.values().stream().mapToInt(List::size).sum();
        final int nonExistentCount = nonExistent.values().stream().mapToInt(List::size).sum();

        final long end = System.currentTimeMillis();

        if (failedCount + nonExistentCount > 0) {
            log.info("아래 {}건의 대상들이 성공적으로 배포되었습니다 [{}/{}] - {}ms", successCount, successCount, totalCount, end - start);
            log.info("\tㄴ배포 성공한 오브젝트 리스트 : {}", success);
            log.warn("Caution : 아래 {}건의 오브젝트들이 배포에 실패하였습니다", failedCount);
            log.warn("\tㄴ배포 실패한 오브젝트 리스트 : {}", failed);
            log.warn("Caution : 아래 {}건의 오브젝트들이 소스 LP에 존재하지 않아 배포에 실패하였습니다", nonExistentCount);
            log.warn("\tㄴ소스 LP에 없는 오브젝트 리스트 : {}", nonExistent);
            return new AdminResponse<>(false, String.format("Failed pull count %d - 실패한 리스트 %s/ 존재하지 않는 오브젝트 리스트 %s"
                    , failedCount + nonExistentCount, failed, nonExistent));
        } else {
            log.info("모든 대상들이 성공적으로 배포되었습니다. ({}건) - {}ms : {}", successCount, end - start, results);
            return new AdminResponse<>(success, successCount);
        }
    }
    @ApiOperation(value="Source LP의 99_change_list 프로시저로 배포 대상을 조회하고, Target LP의 정보를 테이블에 백업 후 배포 (복수)", notes = "\t배포 대상 : 입력한 시간이후 변경된 procedure(프로시저), schedule(예약쿼리), stream(스트림쿼리) 유형의 모든 대상")
    @PostMapping("/targetLP/pull/multi/callProc")
    public AdminResponse<Object> pullMultiCallProc(@RequestBody final DeployMultiMeta.cllProcChangeList deployMultiMeta, HttpServletRequest request) {
        final DateTime findToFrom = summaryFormatter.parseDateTime(deployMultiMeta.getFrom());

        final List findToProcedureList = deploy.get(DeployMapper.find(PROCEDURE)).findAndDeployCallProc(findToFrom);
        final List findToScheduleList = deploy.get(DeployMapper.find(SCHEDULED_QUERY)).findAndDeployCallProc(findToFrom);
        final List findToStreamList = deploy.get(DeployMapper.find(STREAM_QUERY)).findAndDeployCallProc(findToFrom);

        DeployMultiMeta deployMultiMeta1 = new DeployMultiMeta(findToProcedureList, findToScheduleList, findToStreamList);
        return pullMulti(deployMultiMeta1, request);
    }

    @ApiOperation(value="Source LP의 99_change_list 프로시저로 배포대상을 조회한다.", notes = "\t복수(/targetLP/pull/multi) 단위 작업의 파라미터로 활용 가능")
    @PostMapping("/z_admin/sourceLP/callProc/changeObjectList")
    public AdminResponse cllProcToChangeObjectList(@RequestBody final DeployMultiMeta.cllProcChangeList deployMultiMeta, HttpServletRequest request) {
        final DateTime findToFrom = summaryFormatter.parseDateTime(deployMultiMeta.getFrom());

        final List findToProcedureList = deploy.get(DeployMapper.find(PROCEDURE)).findAndDeployCallProc(findToFrom);
        final List findToScheduleList = deploy.get(DeployMapper.find(SCHEDULED_QUERY)).findAndDeployCallProc(findToFrom);
        final List findToStreamList = deploy.get(DeployMapper.find(STREAM_QUERY)).findAndDeployCallProc(findToFrom);

        HashMap<String, List<String>> lists = new HashMap();
        lists.put("procedureList", findToProcedureList);
        lists.put("scheduleList", findToScheduleList);
        lists.put("streamList", findToStreamList);

        ObjectMapper mapper = new ObjectMapper();
        String listAsjsonString = null;
        try{
            listAsjsonString = mapper.writeValueAsString(lists);
        }catch (IOException e){
            e.printStackTrace();
        }
        if (0<listAsjsonString.length()){
            // response body - message 값으로 출력될때 "가 \"로 출력되는 현상을 해결하지 못하여서 콘솔로그로 출력되도록 임시 처리 (magnaru)
            log.info("배포 대상 목록 - {}",listAsjsonString.toString());
            return new AdminResponse<>(true, "조회에 성공하였습니다. 콘솔 로그를 확인하세요.");
        }else {
            return new AdminResponse<>(false, "조회에 싫패하였습니다.");
        }
    }

    private Map<String,String> getRemoteIp(final HttpServletRequest request){
        return new HashMap<String, String>(){{
            put("ip", request.getRemoteAddr());
            put("url", request.getRequestURI());
        }};
    }
}
