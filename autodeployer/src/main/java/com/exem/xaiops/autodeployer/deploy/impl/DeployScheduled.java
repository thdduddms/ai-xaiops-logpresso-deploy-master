package com.exem.xaiops.autodeployer.deploy.impl;

import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClient;
import com.exem.xaiops.autodeployer.deploy.Deploy;
import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import com.exem.xaiops.autodeployer.vo.DeployExecutionResult;
import com.logpresso.client.ScheduledQuery;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.exem.xaiops.autodeployer.Constant.*;

@Component
@Log4j2
public class DeployScheduled extends Deploy<ScheduledQuery> {
    final String targetSystemId;
    final String sourceSystemId;
    private final String deployBackupLPTarget;

    public DeployScheduled(@Value("${logpresso.target.system_id}") final String targetSystemId,
                           @Value("${logpresso.source.system_id}") final String sourceSystemId,
                           final LogpressoClient lPClient,
                           @Value("${logpresso.target.backup-table-name}") final String deployBackupLPTarget) {
        super(lPClient);
        this.targetSystemId = targetSystemId;
        this.sourceSystemId = sourceSystemId;
        this.deployBackupLPTarget = deployBackupLPTarget;
    }

    @Override
    public DeployMapper getMapper() {
        return DeployMapper.SCHEDULES;
    }

    @Override
    public DeployExecutionResult fetchAndDeployBatch() {
        findAndBackupAll();
        List<ScheduledQuery> scheduledQueries = lPClient.getScheduledQueries(SOURCE_LP);
        final List<String> filteredNames = new ArrayList<>();
        final List<ScheduledQuery> filteredSchedules = scheduledQueries.stream()
                .filter(scheduledQuery -> scheduledQuery.getQueryString().contains("| wget") ||
                        scheduledQuery.getQueryString().contains("remote ["))
                .collect(Collectors.toList());

        filteredSchedules.forEach(filtered -> filteredNames.add(filtered.getTitle()));
        log.info("일괄 배포 대상에서 제외된 예약쿼리 목록 : {}", filteredNames);

        scheduledQueries.removeAll(filteredSchedules);

        final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
        final Pattern sysIdPattern = Pattern.compile(regex);

        scheduledQueries.forEach(scheduledQuery -> {
            final String checkSysId = checkSystemId(scheduledQuery, sysIdPattern);
            if (!checkSysId.equals("none")) {
                convertSystemId(scheduledQuery, checkSysId);
            }
        });

        return lPClient.createOrUpdateScheduledQueryBatch(TARGET_LP, scheduledQueries);
    }

    @Override
    public DeployExecutionResult fetchAndDeployMulti(List<String> scheduleNamesToCreate) {

        List<ScheduledQuery> sourceSchedules = lPClient.getScheduledQueries(SOURCE_LP);
        final List<ScheduledQuery> targetSchedulesToCreate = scheduleNamesToCreate.stream()
                .map(targetScheduleName -> sourceSchedules.stream()
                        .filter(sourceSchedule -> sourceSchedule.getTitle().equals(targetScheduleName))
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        targetSchedulesToCreate.forEach(targetSchedule -> {
            final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
            final Pattern sysIdPattern = Pattern.compile(regex);
            final String checkSysId = checkSystemId(targetSchedule, sysIdPattern);
            if (!checkSysId.equals("none")) {
                convertSystemId(targetSchedule, checkSysId);
            }
        });
        return lPClient.createOrUpdateScheduledQueryBatch(TARGET_LP, targetSchedulesToCreate);
    }

    @Override
    public boolean fetchAndDeploy(final String scheduleToFind, String scheduleToCreate) {
        findAndBackup(scheduleToFind);
        ScheduledQuery scheduled = lPClient.getScheduledQuery(SOURCE_LP, scheduleToFind);
        scheduled.setTitle(scheduleToCreate);

        final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
        final Pattern sysIdPattern = Pattern.compile(regex);
        final String checkSysId = checkSystemId(scheduled, sysIdPattern);

        if (!checkSysId.equals("none")) {
            scheduled = convertSystemId(scheduled, checkSysId);
            scheduleToCreate = scheduled.getTitle();
        }
        scheduled.setTitle(scheduleToCreate);
        return lPClient.createOrUpdateScheduledQuery(TARGET_LP, scheduled) != null;
    }

    /**
     * 대상 Logpresso에서 예약쿼리 조회 후 LP 테이블에 저장하고 성공여부를 리턴한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param scheduleToFind 백업 대상
     * @return 성공여부
     */
    public boolean findAndBackup(final String scheduleToFind) {
        return lPClient.insertLPTableScheduledQuery(TARGET_LP, scheduleToFind) > 0;
    }

    /**
     * 대상 Logpresso에서 전체 예약쿼리 조회 후 LP 테이블에 저장하고 성공여부를 리턴한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @return 성공여부
     */
    @Override
    public boolean findAndBackupAll() {
        return lPClient.insertLPTableScheduledQueryAll(TARGET_LP) > 0;
    }

    /**
     * @param from
     * @return
     */
    public List findAndDeployCallProc(final DateTime from){
        return lPClient.callProcToChangeList(SOURCE_LP, SCHEDULED_QUERY, from);
    }

    /**
     * @param scheduledToRestore
     * @return
     */
    @Override
    public String restoreOneFromTargetLP(final String scheduledToRestore, final DateTime from, final DateTime to) {
        final String objectType = getMapper().getId();
        log.info("Restore Request : [{}] {}", objectType, scheduledToRestore);

        final List<Map<String, Object>> restoreData = lPClient.getRestoreData(TARGET_LP, scheduledToRestore, objectType, from, to);
        if (restoreData.size() == 0){
            return null;
        } else {
            return lPClient.createOrUpdateScheduledQuery(TARGET_LP, parseScheduledQuery(restoreData.get(0)));
        }
    }

    @Override
    public DeployExecutionResult restoreMultiFromTargetLP(List<String> scheduledListToRestore, DateTime from, DateTime to) {
        final String objectType = getMapper().getId();
        final List<ScheduledQuery> scheduledListTemp = new ArrayList<>();
        log.info("Restore Request : [{}] {}", objectType, scheduledListTemp.toString());
        scheduledListToRestore.stream()
                .forEach(scheduled -> {
                    List<Map<String, Object>> restoreTemp = lPClient.getRestoreData(TARGET_LP, scheduled, objectType, from, to);
                    if(restoreTemp.size() != 0){
                        scheduledListTemp.add(parseScheduledQuery(restoreTemp.get(0)));
                    }
                });

        return lPClient.createOrUpdateScheduledQueryBatch(TARGET_LP, scheduledListTemp);
    }

    public ScheduledQuery parseScheduledQuery(final Map<String, Object> data) {
        ScheduledQuery s = new ScheduledQuery();
        s.setGuid((String) data.get("guid"));
        s.setTitle((String) data.get("title"));
        s.setCronSchedule((String) data.get("cron_schedule"));
        s.setQueryString((String) data.get("query_string"));

        return s;
    }

    protected String checkSystemId(final ScheduledQuery scheduleToCheck, final Pattern sysIdPattern) {
        final String queryString = scheduleToCheck.getQueryString();
        final String name = scheduleToCheck.getTitle();

        final Matcher matcher = sysIdPattern.matcher(queryString);

        final boolean hasSysIdInQuery = matcher.find();
        final boolean hasSysIdInName = name.contains(sourceSystemId + ".");

        if (hasSysIdInQuery && hasSysIdInName) return "nameAndQuery";
        else if (hasSysIdInQuery) return "query";
        else if (hasSysIdInName) return "name";
        else return "none";
    }

    /**
     * 예약쿼리 제목과 쿼리에 system_id를 직접 입력한 부분을 target Logpresso 의 system_id로 변환
     *
     * @param scheduleToConvertSystemId system_id 값이 변경되기 전의 예약쿼리
     * @return system_id 값이 변경된 쿼리/제목의 예약쿼리
     */
    protected ScheduledQuery convertSystemId(final ScheduledQuery scheduleToConvertSystemId, final String level) {
        final String queryString = scheduleToConvertSystemId.getQueryString();
        final String name = scheduleToConvertSystemId.getTitle();

        final String convertedQuery = queryString
                .replaceAll("system_id\\s*=\\s*\\d+", "system_id = " + targetSystemId)
                .replaceAll("system_id\\s*==\\s*\\d+", "system_id == " + targetSystemId);
        final String convertedName = name.replaceAll("(\\d+\\.)", targetSystemId + ".");

        switch (level) {
            case "query":
                scheduleToConvertSystemId.setQueryString(convertedQuery);
                break;
            case "name":
                scheduleToConvertSystemId.setTitle(convertedName);
                break;
            case "nameAndQuery":
                scheduleToConvertSystemId.setQueryString(convertedQuery);
                scheduleToConvertSystemId.setTitle(convertedName);
                break;
            case "none":
                break;
        }

        return scheduleToConvertSystemId;
    }
}
