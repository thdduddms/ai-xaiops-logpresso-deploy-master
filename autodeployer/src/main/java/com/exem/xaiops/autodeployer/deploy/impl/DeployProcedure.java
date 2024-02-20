package com.exem.xaiops.autodeployer.deploy.impl;

import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClient;
import com.exem.xaiops.autodeployer.deploy.Deploy;
import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import com.exem.xaiops.autodeployer.vo.DeployExecutionResult;
import com.logpresso.client.Procedure;
import com.logpresso.client.ProcedureParameter;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.exem.xaiops.autodeployer.Constant.*;


@Component
@Log4j2
public class DeployProcedure extends Deploy<Procedure> {
    final String targetSystemId;
    final String sourceSystemId;
    private final String deployBackupLPTarget;

    public DeployProcedure(final LogpressoClient lPClient, @Value("${logpresso.target.system_id}") final String targetSystemId,
                           @Value("${logpresso.source.system_id}") final String sourceSystemId, @Value("${logpresso.target.backup_table_name}") final String deployBackupLPTarget) {
        super(lPClient);
        this.targetSystemId = targetSystemId;
        this.sourceSystemId = sourceSystemId;
        this.deployBackupLPTarget = deployBackupLPTarget;
    }

    @Override
    public DeployMapper getMapper() {
        return DeployMapper.PROCEDURES;
    }

    @Override
    public DeployExecutionResult fetchAndDeployBatch() {
        findAndBackupAll();
        List<Procedure> procedures = lPClient.getProcedures(SOURCE_LP);
        final List<String> filteredNames = new ArrayList<>();
        final List<Procedure> filteredProcedures = procedures.stream()
                .filter(procedure -> procedure.getQueryString().contains("| wget")
                        || procedure.getQueryString().contains("remote ["))
                .collect(Collectors.toList());

        filteredProcedures.forEach(filtered -> filteredNames.add(filtered.getName()));
        log.info("일괄 배포 대상에서 제외된 프로시저 목록 : {}", filteredNames);

        procedures.removeAll(filteredProcedures);
        final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
        final Pattern sysIdPattern = Pattern.compile(regex);

        procedures.forEach(procedure -> {
            final String checkSysId = checkSystemId(procedure, sysIdPattern);
            if (!checkSysId.equals("none")) {
                convertSystemId(procedure, checkSysId);
            }
        });
        return lPClient.createOrUpdateProcedureBatch(TARGET_LP, procedures);
    }

    /**
     * Source 로그프레소 서버에서 원본 프로시저명을 찾아서 신규 생성할 프로시저명으로 Merge 하기
     *
     * @param procedureToFind   원본 프로시저명
     * @param procedureToCreate 신규 생성할 프로시저명
     * @return 처리 성공 여부
     */
    @Override
    public boolean fetchAndDeploy(final String procedureToFind, String procedureToCreate) {
        findAndBackup(procedureToFind);
        Procedure proc = lPClient.getProcedure(SOURCE_LP, procedureToFind);
        final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
        final Pattern sysIdPattern = Pattern.compile(regex);
        final String checkSysId = checkSystemId(proc, sysIdPattern);
        if (!checkSysId.equals("none")) {
            proc = convertSystemId(proc, checkSysId);

            procedureToCreate = proc.getName();
        }
        proc.setName(procedureToCreate);
        return lPClient.createOrUpdateProcedure(TARGET_LP, proc) != null;
    }

    /**
     * Target Logpresso에서 프로시저 조회 후 LP 테이블에 저장하고 성공여부를 리턴한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param procedureToFind 백업 대상
     * @return 성공여부
     */
    @Override
    public boolean findAndBackup(final String procedureToFind) {
        return lPClient.insertLPTableProcedure(TARGET_LP, procedureToFind) > 0;     // 여러건 Insert
    }

    /**
     * 대상 Logpresso에서 전체 프로시저 조회 후 LP 테이블에 저장하고 성공여부를 리턴한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @return 성공여부
     */
    @Override
    public boolean findAndBackupAll() {
        return lPClient.insertLPTableProcedureAll(TARGET_LP) > 0;
    }

    /**
     * @param from
     * @return
     */
    public List findAndDeployCallProc(final DateTime from){
        return lPClient.callProcToChangeList(SOURCE_LP, PROCEDURE, from);
    }

    /**
     * 프로시저 제목/쿼리에 system_id 값이 포함되어 있는지 확인
     *
     * @param procedureToCheck system_id 존재 여부를 확인하고자 하는 프로시저
     * @return system_id값 존재 여부
     */
    protected String checkSystemId(final Procedure procedureToCheck, final Pattern sysIdPattern) {
        final String queryString = procedureToCheck.getQueryString();
        final String name = procedureToCheck.getName();

        final Matcher matcher = sysIdPattern.matcher(queryString);

        final boolean hasSysIdInQuery = matcher.find();
        final boolean hasSysIdInName = name.contains("__" + sourceSystemId);

        if (hasSysIdInQuery && hasSysIdInName) return "nameAndQuery";
        else if (hasSysIdInQuery) return "query";
        else if (hasSysIdInName) return "name";
        else return "none";
    }

    /**
     * 프로시저 제목과 쿼리에 system_id를 직접 입력한 부분을 target Logpresso 의 system_id로 변환
     *
     * @param procedureToConvertSystemId system_id 값이 변경되기 전의 프로시저
     * @return system_id 값이 변경된 쿼리/제목의 프로시저
     */
    protected Procedure convertSystemId(final Procedure procedureToConvertSystemId, final String level) {
        final String queryString = procedureToConvertSystemId.getQueryString();
        final String name = procedureToConvertSystemId.getName();

        final String convertedQuery = queryString
                .replaceAll("system_id\\s*=\\s*\\d+", "system_id = " + targetSystemId)
                .replaceAll("system_id\\s*==\\s*\\d+", "system_id == " + targetSystemId);
        final String convertedName = name.replaceAll("(__\\d+)", "__" + targetSystemId);

        switch (level) {
            case "query":
                procedureToConvertSystemId.setQueryString(convertedQuery);
                break;
            case "name":
                procedureToConvertSystemId.setName(convertedName);
                break;
            case "nameAndQuery":
                procedureToConvertSystemId.setQueryString(convertedQuery);
                procedureToConvertSystemId.setName(convertedName);
                break;
            case "none":
                break;
        }

        return procedureToConvertSystemId;
    }

    /**
     * @param procedureToRestore 원복 대상 프로시저명
     * @param from 조회 시작 시점
     * @param to 조회 종료 시점
     * @return
     */
    @Override
    public String restoreOneFromTargetLP(final String procedureToRestore, final DateTime from, final DateTime to) {
        final String objectType = getMapper().getId();
        log.info("Restore Request : [{}] {}", objectType, procedureToRestore);

        final List<Map<String, Object>> restoreData = lPClient.getRestoreData(TARGET_LP, procedureToRestore, objectType, from, to);
        if (restoreData.size() == 0){
            return null;
        } else {
            return lPClient.createOrUpdateProcedure(TARGET_LP, parseProcedure(restoreData.get(0)));
        }
    }

    /**
     * @param procedureListToRestore 원복 대상 프로시저명 리스트
     * @return
     */
    @Override
    public DeployExecutionResult restoreMultiFromTargetLP(final List<String> procedureListToRestore, final DateTime from, final DateTime to) {
        final String objectType = getMapper().getId();
        final List<Procedure> proceduresListTemp = new ArrayList<>();
        log.info("Restore Request : [{}] {}", objectType, procedureListToRestore.toString());
        procedureListToRestore.stream()
            .forEach(procedure -> {
                List<Map<String, Object>> restoreTemp = lPClient.getRestoreData(TARGET_LP, procedure, objectType, from, to);
                if(restoreTemp.size() != 0){
                    proceduresListTemp.add(parseProcedure(restoreTemp.get(0)));
                }
            });

        return lPClient.createOrUpdateProcedureBatch(TARGET_LP, proceduresListTemp);
    }

    /**
     * 프로시저 파라미터 정보을 저장가능한 문자열로 변환하여 리턴한다.
     *
     * @param procedure 프로시저 파라미터 정보
     * @return 프로시저 파라미터 목록
     */
    public List<Map<String, Object>> convertProcedureParameter(final Procedure procedure) {
        final List<ProcedureParameter> params = procedure.getParameters();
        List<Map<String, Object>> data = new ArrayList<>();

        if (params.size() != 0) {
            for (ProcedureParameter param : params) {
                Map<String, Object> kv = new HashMap<>();
                kv.put("name", param.getName());
                kv.put("description", param.getDescription());
                kv.put("type", param.getType());
                kv.put("key", param.getKey());
                data.add(kv);
            }
        }
        return data;
    }

    /**
     * 보안 그룹을 저장가능한 문자열로 변환하여 리턴한다.
     *
     * @param procedure 보안 그룹 정보
     * @return 보안 그룹 목록
     */
    public String convertGrantGroups(final Procedure procedure) {
        final Set<String> groups = procedure.getGrantGroups();
        StringBuilder data = new StringBuilder();
        String str;
        for (String group : groups) {
            data.append(lPClient.getSecurityGroupFromGuid(TARGET_LP, group).getName());
            data.append(",");
        }
        if (data.toString().endsWith(",")) {
            data.deleteCharAt(data.length() - 1);
        }
        if ("".equals(data.toString()) || 0 == data.length()) {
            str = null;
        } else {
            str = data.toString();
        }
        return str;
    }

    /**
     * 사용자 목록을 저장가능한 문자열로 변환하여 리턴한다.
     *
     * @param procedure 사용자 정보
     * @return 사용자 목록
     */
    public String convertGrants(final Procedure procedure) {
        final Set<String> grants = procedure.getGrantLogins();
        StringBuilder data = new StringBuilder();
        String str;
        for (String grant : grants) {
            data.append(grant);
            data.append(",");
        }
        if (data.toString().endsWith(",")) {
            data.deleteCharAt(data.length() - 1);
        }
        if ("".equals(data.toString()) || 0 == data.length()) {
            str = null;
        } else {
            str = data.toString();
        }
        return str;
    }

    public Procedure parseProcedure(final Map<String, Object> data) {
        List<ProcedureParameter> parameters = new ArrayList<>();
        final Object[] parametersObj = (Object[]) data.get("parameters");
        for (Object obj : parametersObj) {
            ProcedureParameter pp = new ProcedureParameter();
            pp.setKey(((HashMap) obj).get("key").toString());
            pp.setType(((HashMap) obj).get("type").toString());
            if (((HashMap) obj).get("name") != null) {
                pp.setName(((HashMap) obj).get("name").toString());
            }
            if (((HashMap) obj).get("description") != null) {
                pp.setDescription(((HashMap) obj).get("description").toString());
            }
            parameters.add(pp);
        }
        Procedure p = new Procedure();
        p.setName((String) data.get("name"));
        p.setDescription((String) data.get("description"));
        p.setQueryString((String) data.get("query_string"));
        p.setParameters(parameters);

        return p;
    }

    @Override
    public DeployExecutionResult fetchAndDeployMulti(List<String> procNamesToCreate) {

        final List<Procedure> sourceProcedures = lPClient.getProcedures(SOURCE_LP);
        final List<Procedure> targetProceduresToCreate = procNamesToCreate.stream()
                .map(targetProcedureName -> sourceProcedures.stream()
                        .filter(sourceProcedure -> sourceProcedure.getName().equals(targetProcedureName))
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        targetProceduresToCreate.forEach(targetProcedure -> {
            final String regex = "(system_id\\s*={1,2}\\s*" + sourceSystemId + "\\b+)";
            final Pattern sysIdPattern = Pattern.compile(regex);
            final String checkSysId = checkSystemId(targetProcedure, sysIdPattern);
            if (!checkSysId.equals("none")) {
                convertSystemId(targetProcedure, checkSysId);
            }
        });
        return lPClient.createOrUpdateProcedureBatch(TARGET_LP, targetProceduresToCreate);
    }
}
