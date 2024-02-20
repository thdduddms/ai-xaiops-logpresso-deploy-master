package com.exem.xaiops.autodeployer.deploy.impl;

import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClient;
import com.exem.xaiops.autodeployer.deploy.Deploy;
import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import com.exem.xaiops.autodeployer.vo.DeployExecutionResult;
import com.logpresso.client.StreamQuery;
import com.logpresso.client.StreamQueryStatus;
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
public class DeployStream extends Deploy<StreamQuery> {
    final String targetSystemId;
    final String sourceSystemId;
    private final String deployBackupLPTarget;
    public DeployStream(LogpressoClient lPClient,
                        @Value("${logpresso.target.system_id}") final String targetSystemId,
                        @Value("${logpresso.source.system_id}") final String sourceSystemId,
                        @Value("${logpresso.target.backup_table_name}") final String deployBackupLPTarget) {
        super(lPClient);
        this.deployBackupLPTarget = deployBackupLPTarget;
        this.sourceSystemId=sourceSystemId;
        this.targetSystemId=targetSystemId;
    }

    @Override
    public DeployMapper getMapper() {return DeployMapper.STREAMS;}

    /**
     *
     * @param streamToFind
     * @param streamToCreate
     * @return
     */
    @Override
    public boolean fetchAndDeploy(final String streamToFind, String streamToCreate) {
        findAndBackup(streamToFind);
        StreamQuery stream = lPClient.getStreamQuery(SOURCE_LP, streamToFind).getStreamQuery();
        stream.setName(streamToCreate);

        final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
        final Pattern sysIdPattern = Pattern.compile(regex);
        final String checkSysId = checkSystemId(stream, sysIdPattern);
        if(!checkSysId.equals("none")) {
            stream = convertSystemId(stream, checkSysId);
            streamToCreate = stream.getName();
        }
        stream.setName(streamToCreate);
        return lPClient.createOrUpdateStream(TARGET_LP, stream) != null;
    }

    /**
     * 대상 Logpresso에서 스트림 쿼리 조회 후 LP 테이블에 저장하고 성공여부를 리턴한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     * @param streamToFind 백업 대상
     * @return 백업 성공여부
     */
    @Override
    public boolean findAndBackup(final String streamToFind) {
        return lPClient.insertLPTableStreamQuery(TARGET_LP, streamToFind) > 0;
    }

    /**
     * 대상 Logpresso에서 전체 스트림 쿼리 조회 후 LP 테이블에 저장하고 성공여부를 리턴한다.
     * @return 성공여부
     */
    @Override
    public boolean findAndBackupAll() {
        return lPClient.insertLPTableStreamQueryAll(TARGET_LP) > 0;
    }

    /**
     * @param from
     * @return
     */
    public List findAndDeployCallProc(final DateTime from){
        return lPClient.callProcToChangeList(SOURCE_LP, STREAM_QUERY, from);
    }

    /**
     *
     * @param streamToRestore
     * @return
     */
    @Override
    public String restoreOneFromTargetLP(final String streamToRestore, final DateTime from, final DateTime to) {
        final String objectType = getMapper().getId();
        log.info("Restore Request : [{}] {}", objectType, streamToRestore);

        final List<Map<String, Object>> restoreData = lPClient.getRestoreData(TARGET_LP, streamToRestore, objectType, from, to);
        if (restoreData.size() == 0){
            return null;
        } else {
            return lPClient.createOrUpdateStream(TARGET_LP, parseStreamQuery(restoreData.get(0)));
        }
    }

    @Override
    public DeployExecutionResult restoreMultiFromTargetLP(List<String> streamListToRestore, DateTime from, DateTime to) {
        final String objectType = getMapper().getId();
        final List<StreamQueryStatus> streamListTemp = new ArrayList<>();
        log.info("Restore Request : [{}] {}", objectType, streamListToRestore.toString());
        streamListToRestore.stream()
                .forEach(stream -> {
                    List<Map<String, Object>> restoreTemp = lPClient.getRestoreData(TARGET_LP, stream, objectType, from, to);
                    if(restoreTemp.size() != 0){
                        StreamQueryStatus st = new StreamQueryStatus();
                        st.setStreamQuery(parseStreamQuery(restoreTemp.get(0)));
                        streamListTemp.add(st);
                    }
                });
      return lPClient.createOrUpdateStreamBatch(TARGET_LP, streamListTemp);
    }

    public StreamQuery parseStreamQuery(final Map<String, Object> data){
        StreamQuery s = new StreamQuery();
        s.setName((String)data.get("name"));
        s.setQueryString((String)data.get("query"));
        s.setDescription((String)data.get("description"));
        s.setInterval((Integer)data.get("interval"));
        s.setSourceType((String)data.get("source_type"));

        List<String> sources = new ArrayList<>();
        Object[] sourcesObj = (Object[]) data.get("sources");
        for(Object obj: sourcesObj){
            sources.add(obj.toString());
        }
        s.setSources(sources);

        return s;
    }

    @Override
    public DeployExecutionResult fetchAndDeployBatch() {
        findAndBackupAll();
        List<StreamQueryStatus> sourceStreamQueries = lPClient.getStreamQueries(SOURCE_LP);
        final List<String> filteredNames = new ArrayList<>();
        final List<StreamQueryStatus> filteredStreams = sourceStreamQueries.stream()
                .filter(stream ->
                        stream.getStreamQuery().getName().contains("dummy") ||
                                stream.getStreamQuery().getName().startsWith("cep_event") ||
                                stream.getStreamQuery().getName().startsWith("cep_metric") ||
                                stream.getStreamQuery().getName().startsWith("cep_apdex") ||
                                stream.getStreamQuery().getQueryString().contains("| wget"))
                .collect(Collectors.toList());

        filteredStreams.forEach(filtered -> filteredNames.add(filtered.getStreamQuery().getName()));
        log.info("일괄 배포 대상에서 제외된 스트림쿼리 목록 : {}", filteredNames);

        sourceStreamQueries.removeAll(filteredStreams);
        final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
        Pattern sysIdPattern = Pattern.compile(regex);

        sourceStreamQueries.forEach(stream -> {
            final String checkSysId = checkSystemId(stream.getStreamQuery(), sysIdPattern);
            if(!checkSysId.equals("none")) {
                convertSystemId(stream.getStreamQuery(), checkSysId);
            }
        });
        return lPClient.createOrUpdateStreamBatch(TARGET_LP, sourceStreamQueries);
    }

    @Override
    public DeployExecutionResult fetchAndDeployMulti(List<String> streamNamesToCreate) {

        final List<StreamQueryStatus> sourceStreams = lPClient.getStreamQueries(SOURCE_LP);
        final List<StreamQueryStatus> targetStreamsToCreate = streamNamesToCreate.stream()
                .map(targetStreamName -> sourceStreams.stream()
                        .filter(sourceStream -> sourceStream.getStreamQuery().getName().equals(targetStreamName))
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        targetStreamsToCreate.forEach(targetStream -> {
            final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
            final Pattern sysIdPattern = Pattern.compile(regex);
            final String checkSysId = checkSystemId(targetStream.getStreamQuery(), sysIdPattern);
            if(!checkSysId.equals("none")) {
                convertSystemId(targetStream.getStreamQuery(), checkSysId);
            }
        });
        return lPClient.createOrUpdateStreamBatch(TARGET_LP, targetStreamsToCreate);
    }

    protected String checkSystemId(final StreamQuery streamToCheck, final Pattern sysIdPattern) {
        final String queryString = streamToCheck.getQueryString();
        final String name = streamToCheck.getName();

        final Matcher matcher = sysIdPattern.matcher(queryString);

        final boolean hasSysIdInQuery = matcher.find();
        final boolean hasSysIdInName = name.contains(sourceSystemId+".");

        if (hasSysIdInQuery && hasSysIdInName) return "nameAndQuery";
        else if (hasSysIdInQuery) return "query";
        else if (hasSysIdInName) return "name";
        else return "none";
    }
    /**
     * 스트림쿼리 제목과 쿼리에 system_id를 직접 입력한 부분을 target Logpresso 의 system_id로 변환
     * @param streamToConvertSystemId system_id 값이 변경되기 전의 스트림쿼리
     * @return system_id 값이 변경된 쿼리/제목의 스트림쿼리
     */
    protected StreamQuery convertSystemId(final StreamQuery streamToConvertSystemId, final String level) {
        final String queryString = streamToConvertSystemId.getQueryString();
        final String name = streamToConvertSystemId.getName();

        final String convertedQuery = queryString
                .replaceAll("system_id\\s*=\\s*\\d+", "system_id = " + targetSystemId)
                .replaceAll("system_id\\s*==\\s*\\d+", "system_id == " + targetSystemId);
        final String convertedName = name.replaceAll("(\\d+\\.)", targetSystemId+".");

        switch (level) {
            case "query":
                streamToConvertSystemId.setQueryString(convertedQuery);
                break;
            case "name":
                streamToConvertSystemId.setName(convertedName);
                break;
            case "nameAndQuery":
                streamToConvertSystemId.setQueryString(convertedQuery);
                streamToConvertSystemId.setName(convertedName);
                break;
            case "none":
                break;
        }

        return streamToConvertSystemId;
    }
}
