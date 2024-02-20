package com.exem.xaiops.autodeployer.config.logpresso;

import com.exem.xaiops.autodeployer.exceptions.ErrorCode;
import com.exem.xaiops.autodeployer.exceptions.impl.LPConnectionException;
import com.exem.xaiops.autodeployer.exceptions.impl.LogpressoException;
import com.exem.xaiops.autodeployer.exceptions.impl.NoObjectFoundException;
import com.exem.xaiops.autodeployer.vo.DeployExecutionResult;
import com.logpresso.client.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.exem.xaiops.autodeployer.Constant.*;
import static com.exem.xaiops.autodeployer.LPConstant.*;
import static org.apache.commons.lang3.StringUtils.indexOf;
import static org.apache.commons.text.StringSubstitutor.replace;

import java.util.stream.IntStream;


@Log4j2
@Component
public class LogpressoClient {
    private final String sourceHost;
    private final int sourcePort;
    private final String sourceUser;
    private final String sourcePassword;
    private final String targetHost;
    private final int targetPort;
    private final String targetUser;
    private final String targetPassword;

    private Logpresso client = null;
    private Cursor cursor = null;

    @Value("${logpresso.target.backup-table-name}")
    protected String backupTable;
    @Value("${logpresso.target.history_table_name}")
    protected String deployHistoryTable;


    public LogpressoClient(@Value("${logpresso.source.host}") final String sourceHost, @Value("${logpresso.source.port}") final int sourcePort,
                           @Value("${logpresso.source.user}") final String sourceUser, @Value("${logpresso.source.password}") final String sourcePassword,
                           @Value("${logpresso.target.host}") final String targetHost, @Value("${logpresso.target.port}") final int targetPort,
                           @Value("${logpresso.target.user}") final String targetUser, @Value("${logpresso.target.password}") final String targetPassword) {
        this.sourceHost = sourceHost;
        this.sourcePort = sourcePort;
        this.sourceUser = sourceUser;
        this.sourcePassword = sourcePassword;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.targetUser = targetUser;
        this.targetPassword = targetPassword;
    }

    protected void printStackTrace() {
        StackTraceElement[] ste = new Throwable().getStackTrace();
        List<StackTraceElement> stack = Arrays.stream(ste).skip(1)
                .filter(element -> element.getClassName().startsWith("com.exem.xaiops")).collect(Collectors.toList());
        for (StackTraceElement element : stack) {
            log.error("{} [{}:{}]", element.getClassName(), element.getMethodName(), element.getLineNumber());
        }
    }

    protected String getStackTrace() {
        StackTraceElement[] ste = new Throwable().getStackTrace();
        List<StackTraceElement> stack = Arrays.stream(ste).skip(1)
                .filter(element -> element.getClassName().startsWith("com.exem.xaiops")).collect(Collectors.toList());
        String method = "";
        for (StackTraceElement element : stack) {
            method += StringUtils.join(element.getClassName(), "[", element.getMethodName(), ":", element.getLineNumber(), "]") + "\n";
        }
        return method;
    }

    public Logpresso lpServerConnect(final String connectLP) {
        try {
            client = new Logpresso();
            if (connectLP.equals(SOURCE_LP))
                client.connect(sourceHost, sourcePort, sourceUser, sourcePassword);
            else if (connectLP.equals(TARGET_LP))
                client.connect(targetHost, targetPort, targetUser, targetPassword);
            else {
                log.error("'source'와 'target'만 입력 가능합니다.");
                throw new RuntimeException("API Fail : 'source'와 'target'만 입력 가능합니다.");
            }
        } catch (IllegalArgumentException e) {
            printStackTrace();
            throw new LPConnectionException(ErrorCode.LP_CONNECTION_INFO_EXCEPTION, "application.yml에 로그프레소 접속정보를 확인하세요", connectLP, e);
        } catch (IOException ioe) {
            printStackTrace();
            throw new LPConnectionException(ErrorCode.LP_CONNECT_EXCEPTION, "로그프레소의 상태를 확인해주세요", connectLP, ioe);
        }
        return client;
    }

    public void lpServerClose() {
        try {
            if (client != null) client.close();
        } catch (IllegalArgumentException | IOException e) {
            printStackTrace();
            throw new LPConnectionException(ErrorCode.LP_CONNECT_EXCEPTION, "로그프레소와 연결 중단하는 도중 예외 발생하였습니다");
        }
    }

    /**
     * @param connectLP
     * @param query
     * @return
     */
    public List<Map<String, Object>> simpleQuery(final String connectLP, final String query) {
        final List<Map<String, Object>> result = new ArrayList<>();

        try {
            client = lpServerConnect(connectLP);
            cursor = client.query(query);

            while (cursor.hasNext()) {
                result.add(cursor.next().toMap());
            }
        } catch (MessageException | IOException e) {
            printStackTrace();
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, query, connectLP, e);
        } finally {
            try {
                if (cursor != null) cursor.close();
                if (client != null) client.close();
            } catch (IOException e) {
                printStackTrace();
                throw new LPConnectionException(ErrorCode.LP_CONNECT_EXCEPTION, "로그프레소와 연결 중단하는 도중 예외 발생하였습니다");
            }
        }

        return result;
    }

    public void insertLP(final String connectLP, final String tableName, final List<Tuple> tuples) throws IOException {
        Logpresso client = null;
        TableSchema tableSchema = new TableSchema();

        try {
            client = lpServerConnect(connectLP);
            tableSchema = client.getTableSchema(tableName);
        } catch(MessageException | IOException ioe) {
            client.createTable(tableName, "v3p");       // option : v3p, deflate, row
            log.info("{} 테이블이 존재하지 않아 생성하였습니다.", tableName);
        } finally {
            if (tuples.size() > 0) {
                try {
                    client = lpServerConnect(connectLP);
                    Future<Integer> future = client.insert(tableName, tuples);
                    log.debug("tuple size: {}", future.get(10L, TimeUnit.SECONDS));
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    log.error("logpresso insert error: {}", tableName, e);
                    throw new RuntimeException(e);
                } finally {
                    lpServerClose();
                }
            } else {
                log.debug("tuples size: 0");
            }
        }
    }

    /**
     * source나 target Logpresso에서 전체 프로시저 정보롤 조회한다.
     *
     * @param connectLP 접속대상 Lopgresso (source 또는 target)
     * @return 프로시저 정보
     */
    public List<Procedure> getProcedures(final String connectLP) {
        List<Procedure> procedures;

        try {
            client = lpServerConnect(connectLP);
            procedures = client.listProcedures();
            if (procedures.size() == 0) {
                printStackTrace();
                throw new NoObjectFoundException(ErrorCode.PROCEDURES_NOT_FOUND, "등록된 프로시저가 없습니다", connectLP);
            }
        } catch (IOException ioe) {
            printStackTrace();
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "프로시저 조회 중 예외가 발생하였습니다", connectLP, ioe);
        } finally {
            lpServerClose();
        }
        return procedures;
    }

    /**
     * source나 target Logpresso에서 하나의 프로시저 정보를 조회한다.
     *
     * @param connectLP     접속대상 Lopgresso (source 또는 target)
     * @param procedureName 검색할 프로시저명
     * @return
     */
    public Procedure getProcedure(final String connectLP, final String procedureName) {
        List<Procedure> allProcedures = getProcedures(connectLP);
        Procedure procedure = null;
        if (allProcedures.size() != 0) {
            procedure = allProcedures.stream()
                    .filter(procedureInfo -> procedureInfo.getName().equals(procedureName))
                    .findFirst().orElse(null);
        }
        if (procedure == null) {
            printStackTrace();
            throw new NoObjectFoundException(ErrorCode.PROCEDURE_NOT_FOUND, "프로시저가 없습니다", connectLP, procedureName);
        }
        return procedure;
    }

    /**
     * source나 target Logpresso에서 테이블 스키마 정보롤 조회한다.
     * Exception 테이블이 존재하지 않거나, 액세스 권한이 없을 경우 발생
     *
     * @param client    Lopgresso 접속 객체
     * @param tableName 검색할 테이블명
     * @return 테이블 스키마 정보
     * @throws IOException
     */
    public TableSchema getTableSchema(final Logpresso client, final String tableName) throws IOException {
        TableSchema tableSchema = new TableSchema();

        try {
            tableSchema = client.getTableSchema(tableName);
        } catch (IOException | MessageException ioe) { // 테이블이 존재하지 않거나, 액세스 권한이 없습니다.
            client.createTable(tableName, "v3p");       // option : v3p, deflate, row
            log.info("{} 테이블이 존재하지 않아 생성하였습니다.", tableName);        // 백업용 테이블이 없는 경우, 생성되는 정상 동작
        } catch (Exception e) {
            printStackTrace();
            // 2023-03-31 TARGET_LP에서만 백업을 진행하므로 TARGET_LP를 넣어두었다.
            throw new LogpressoException(ErrorCode.LP_TABLE_SCHEMA_EXCEPTION, "테이블 스키마 조회 중 예외 발생하였습니다");
        }
        return tableSchema;
    }

    /**
     * 한 개의 프로시저를 조회하여 logpresso 테이블에 백업을 위해 Insert 한다.zkapfk
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param connectLP       접속대상 Lopgresso 서버 (source 또는 target)
     * @param procedureToFind 백업 대상 프로시저명
     * @return insert 건수
     */
    public int insertLPTableProcedure(final String connectLP, final String procedureToFind) {
        int result = 0;

        try {
            client = lpServerConnect(connectLP);
            getTableSchema(client, backupTable);

            final Procedure procedure = client.listProcedures().stream()
                    .filter(sq -> sq.getName().equals(procedureToFind))
                    .findFirst().orElse(null);
            if (null != procedure) {
                List<Map<String, Object>> parameters = convertProcedureParameter(procedure);
                String grant_groups = convertGrantGroups(procedure);
                String grants = convertGrants(procedure);

                List<Tuple> rows = new ArrayList<>();
                Tuple cols = new Tuple();

                // required
                cols.put("object_type", PROCEDURE);
                cols.put("name", procedure.getName());
                cols.put("owner", procedure.getOwner());
                cols.put("query_string", procedure.getQueryString());
                cols.put("parameters", parameters);
                // optional
                cols.put("grant_groups", grant_groups);
                // 그룹 guid 로 조회되어서 Logpresso 서버마다 그룹명은 같지만 실제 키값은 다르기 때문에 복제가 어렵다.
                // 등록할때 필요한 guid 대신 이름을 저장하고, 등록할때는 같은 이름의 guid 를 조회하여 저장해야함.
                cols.put("grants", grants);
                cols.put("description", procedure.getDescription());

//            rows.add(cols);           // 다수 행을 저장할때 List 로 처리

                Future<Integer> insertRow = client.insert(backupTable, cols);
                if (insertRow.isDone() && 0 <= insertRow.get()) {
                    client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.DATA_NOT_INSERTED_EXCEPTION.toString() + " 데이터 입력 중 예외가 발생하였습니다"));
                    throw new LogpressoException(ErrorCode.DATA_NOT_INSERTED_EXCEPTION, "데이터 입력 중 예외가 발생하였습니다", connectLP);
                }
                ++result;
                log.info("LP Table Backup Success : [{}] {} - inserted ", PROCEDURE, cols.get("name"));
            } else {
                log.info("{} 로그프레소에서 {}를 찾을 수 없어 백업하지 않았습니다", connectLP, procedureToFind);
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.LP_INSERT_EXCEPTION.toString() + " 백업 중 예외가 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_INSERT_EXCEPTION, procedureToFind + " 백업 중 예외가 발생하였습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return result;
    }

    /**
     * 전체 프로시저를 조회하여 logpresso 테이블에 백업을 위해 Insert 한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @return insert 건수
     */
    public int insertLPTableProcedureAll(final String connectLP) {
        int result = 0;
        List<Tuple> rows = new ArrayList<>();
        Tuple cols;

        try {
            client = lpServerConnect(connectLP);
            getTableSchema(client, backupTable);

            final List<Procedure> allProcedures = client.listProcedures();

            if (allProcedures.size() != 0) {
                for (Procedure procedure : allProcedures) {
                    cols = new Tuple();
                    List<Map<String, Object>> parameters = convertProcedureParameter(procedure);
                    String grant_groups = convertGrantGroups(procedure);
                    String grants = convertGrants(procedure);

                    // required
                    cols.put("object_type", PROCEDURE);
                    cols.put("name", procedure.getName());
                    cols.put("owner", procedure.getOwner());
                    cols.put("query_string", procedure.getQueryString());
                    cols.put("parameters", parameters);
                    // optional
                    cols.put("grant_groups", grant_groups);
                    // 그룹 guid 로 조회되어서 Logpresso 서버마다 그룹명은 같지만 실제 키값은 다르기 때문에 복제가 어렵다.
                    // 등록할때 필요한 guid 대신 이름을 저장하고, 등록할때는 같은 이름의 guid 를 조회하여 저장해야함.
                    cols.put("grants", grants);
                    cols.put("description", procedure.getDescription());

//                rows.add(cols);           // 다수 행을 저장할때 List 로 처리

                    Future<Integer> insertRow = client.insert(backupTable, cols);
                    if (insertRow.isDone() && 0 <= insertRow.get()) {
                        client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.DATA_NOT_INSERTED_EXCEPTION.toString()));
                        throw new LogpressoException(ErrorCode.DATA_NOT_INSERTED_EXCEPTION, connectLP);
                    }
                    ++result;
                    log.info("LP Table Backup Success : [{}] {} - inserted ", PROCEDURE, cols.get("name"));
                }
            } else {
                printStackTrace();
                client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.PROCEDURES_NOT_FOUND.toString() + "백업 과정에서 예외가 발생하였습니다"));
                throw new NoObjectFoundException(ErrorCode.PROCEDURES_NOT_FOUND, "백업 과정에서 예외가 발생하였습니다", connectLP);
            }
            log.info("[{}] {} 건 insert 처리 완료", PROCEDURE, result);
        } catch (InterruptedException | ExecutionException | IOException e) {
            printStackTrace();
            final String msg = String.format("%s건까지 처리 완료", result);
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.PROCEDURE_BACKUP_FAIL.toString()));
            throw new LogpressoException(ErrorCode.PROCEDURE_BACKUP_FAIL, msg, connectLP, e);
        } finally {
            lpServerClose();
        }
        return result;
    }

    /**
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @param procedure
     * @return
     */
    public String createOrUpdateProcedure(final String connectLP, final Procedure procedure) {
        String name = "";
        try {
            client = lpServerConnect(connectLP);
            final List<Procedure> listFromTarget = client.listProcedures();
            final String procName = procedure.getName();

            Procedure result = listFromTarget.stream()
                    .filter(proc -> proc.getName().equals(procName))
                    .findFirst().orElse(null);
            name = procName;

            if (procedure.getQueryString().contains("| # {")) {
                procedure.setQueryString(convertExcludeSyntax(procedure));
                log.info("Exclude Syntax Success : [{}] {} - excluded", PROCEDURE, name);
            }
            if(name.startsWith("server_")){
                procedure.setQueryString(includeWarning(procedure.getQueryString()));
            }

            if (result != null) {
                // to-be : guid 처리 필요
                client.updateProcedure(procedure);
                log.info("Update Success : [{}] {} - {} updated", PROCEDURE, name, PROCEDURE);
            } else {
                client.createProcedure(procedure);
                log.info("Create Success : [{}] {} - {} created", PROCEDURE, name, PROCEDURE);
            }
        } catch (MessageException | IOException e) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + "프로시저 생성/수정 중 예외 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "프로시저 생성/수정 중 예외 발생하였습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return name;
    }

    public DeployExecutionResult createOrUpdateProcedureBatch(final String connectLP, final List<Procedure> procedures) {
        List<String> createdList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        try {
            client = lpServerConnect(connectLP);

            List<Procedure> list = client.listProcedures();
            for (Procedure procedure : procedures) {
                final String name = procedure.getName();
                Procedure result = list.stream()
                        .filter(proc -> proc.getName().equals(name))
                        .findFirst().orElse(null);
                if(name.startsWith("server_")) {
                    procedure.setQueryString(includeWarning(procedure.getQueryString()));
                }
                try {
                    if (result != null) {
                        client.updateProcedure(procedure);
                    } else {
                        client.createProcedure(procedure);
                    }
                    createdList.add(name);
                } catch (MessageException | IOException e) {
                    failedList.add(name);
                }
            }
        } catch (Exception e) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 프로시저 일괄 생성/수정 중 예외 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "프로시저 일괄 생성/수정 중 예외 발생하였습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return new DeployExecutionResult(createdList, failedList);
    }

    /**
     * 한 개의 예약쿼리를 조회하여 logpresso 테이블에 백업을 위해 Insert 한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param connectLP      접속대상 Lopgresso 서버 (source 또는 target)
     * @param scheduleToFind 백업 대상 프로시저명
     * @return insert 건수
     */
    public int insertLPTableScheduledQuery(final String connectLP, final String scheduleToFind) {
        int result = 0;

        try {
            client = lpServerConnect(connectLP);
            getTableSchema(client, backupTable);

            final ScheduledQuery schedule = client.listScheduledQueries().stream()
                    .filter(sq -> sq.getTitle().equals(scheduleToFind))
                    .findFirst().orElse(null);
            if (null != schedule) {
                List<Tuple> rows = new ArrayList<>();
                Tuple cols = new Tuple();

                // required
                cols.put("object_type", SCHEDULED_QUERY);
                cols.put("guid", schedule.getGuid());
                cols.put("title", schedule.getTitle());
                cols.put("cron_schedule", schedule.getCronSchedule());
                cols.put("owner", schedule.getOwner());
                cols.put("query", schedule.getQueryString());
                // optional
                cols.put("use_alert", schedule.isUseAlert());
                cols.put("alert_query", schedule.getAlertQuery());
                cols.put("suppress_interval", schedule.getSuppressInterval());
                cols.put("mail_profile", schedule.getMailProfile());
                cols.put("mail_from", schedule.getMailFrom());
                cols.put("mail_to", schedule.getMailTo());
                cols.put("mail_subject", schedule.getMailSubject());

//            rows.add(cols);           // 다수 행을 저장할때 List 로 처리

                Future<Integer> insertRow = client.insert(backupTable, cols);
                if (insertRow.isDone() && 0 <= insertRow.get()) {
                    client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.DATA_NOT_INSERTED_EXCEPTION.toString()));
                    throw new LogpressoException(ErrorCode.DATA_NOT_INSERTED_EXCEPTION, connectLP);
                }
                ++result;
                log.info("LP Table Backup Success : [{}] {} - inserted ", SCHEDULED_QUERY, cols.get("title"));
            } else {
                log.info("{} 로그프레소에서 {}를 찾을 수 없어 백업하지 않았습니다", connectLP, scheduleToFind);
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.LP_INSERT_EXCEPTION.toString() + " 백업 중 예외가 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_INSERT_EXCEPTION, scheduleToFind + " 백업 중 예외가 발생하였습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return result;
    }

    /**
     * 전체 예약쿼리를 조회하여 logpresso 테이블에 백업을 위해 Insert 한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @return insert 건수
     */
    public int insertLPTableScheduledQueryAll(final String connectLP) {
        int result = 0;
        List<Tuple> rows = new ArrayList<>();
        Tuple cols;

        try {
            client = lpServerConnect(connectLP);
            getTableSchema(client, backupTable);

            List<ScheduledQuery> allSchedules = client.listScheduledQueries();
            if (allSchedules.size() != 0) {
                for (ScheduledQuery schedule : allSchedules) {
                    cols = new Tuple();
                    // required
                    cols.put("object_type", SCHEDULED_QUERY);
                    cols.put("guid", schedule.getGuid());
                    cols.put("title", schedule.getTitle());
                    cols.put("cron_schedule", schedule.getCronSchedule());
                    cols.put("owner", schedule.getOwner());
                    cols.put("query", schedule.getQueryString());
                    // optional
                    cols.put("use_alert", schedule.isUseAlert());
                    cols.put("alert_query", schedule.getAlertQuery());
                    cols.put("suppress_interval", schedule.getSuppressInterval());
                    cols.put("mail_profile", schedule.getMailProfile());
                    cols.put("mail_from", schedule.getMailFrom());
                    cols.put("mail_to", schedule.getMailTo());
                    cols.put("mail_subject", schedule.getMailSubject());

//                rows.add(cols);           // 다수 행을 저장할때 List 로 처리

                    Future<Integer> insertRow = client.insert(backupTable, cols);
                    if (insertRow.isDone() && 0 <= insertRow.get()) {
                        throw new LogpressoException(ErrorCode.DATA_NOT_INSERTED_EXCEPTION, connectLP);
                    }
                    ++result;
                    log.info("LP Table Backup Success : [{}] {} - inserted ", SCHEDULED_QUERY, cols.get("title"));
                }
            } else {
                printStackTrace();
                client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.SCHEDULED_QUERIES_NOT_FOUND.toString() + " 백업 과정에서 예외가 발생하였습니다"));
                throw new NoObjectFoundException(ErrorCode.SCHEDULED_QUERIES_NOT_FOUND, "백업 과정에서 예외가 발생하였습니다", connectLP);
            }
            log.info("[{}] {} 건 insert 처리 완료", SCHEDULED_QUERY, result);
        } catch (InterruptedException | ExecutionException | IOException e) {
            printStackTrace();
            final String msg = String.format("%s건까지 처리 완료", result);
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.LP_INSERT_EXCEPTION.toString()));
            throw new LogpressoException(ErrorCode.LP_INSERT_EXCEPTION, msg, connectLP, e);
        } finally {
            lpServerClose();
        }
        return result;
    }

    /**
     * @param connectLP
     * @param schedules
     * @return
     */
    public DeployExecutionResult createOrUpdateScheduledQueryBatch(final String connectLP, final List<ScheduledQuery> schedules) {
        List<String> createdList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        try {
            client = lpServerConnect(connectLP);

            List<ScheduledQuery> list = client.listScheduledQueries();
            for (ScheduledQuery scheduled : schedules) {
                final String name = scheduled.getTitle();
                ScheduledQuery result = list.stream()
                        .filter(sq -> sq.getTitle().equals(name))
                        .findFirst().orElse(null);
                try {
                    if (result != null) {
                        scheduled.setGuid(result.getGuid());
                        client.updateScheduledQuery(scheduled);
                    } else {
                        client.createScheduledQuery(scheduled);
                    }
                    createdList.add(name);
                } catch (MessageException | IOException e) {
                    failedList.add(name);
                }
            }
        } catch (Exception e) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 예약쿼리 일괄 생성/수정 중 예외 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "예약쿼리 일괄 생성/수정 중 예외 발생하였습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return new DeployExecutionResult(createdList, failedList);
    }

    /**
     * source나 target에서 LP의 예약쿼리 한 개를 조회
     *
     * @param scheduleToFind 조회할 예약쿼리명
     * @return 예약쿼리명과 일치하는 예약쿼리 정보
     */
    public ScheduledQuery getScheduledQuery(final String connectLP, final String scheduleToFind) {
        final ScheduledQuery schedule = getScheduledQueries(connectLP).stream()
                .filter(sq -> sq.getTitle().equals(scheduleToFind))
                .findFirst().orElse(null);
        if (schedule == null) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.SCHEDULED_QUERY_NOT_FOUND.toString() + " 예약쿼리가 없습니다"));
            throw new NoObjectFoundException(ErrorCode.SCHEDULED_QUERY_NOT_FOUND, "예약쿼리가 없습니다", scheduleToFind);
        }
        return schedule;
    }

    /**
     * source나 target에서 LP의 예약쿼리 정보 목록를 조회한다.
     *
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @return 예약쿼리 정보 목록 반환
     */
    public List<ScheduledQuery> getScheduledQueries(final String connectLP) {
        List<ScheduledQuery> scheduledQueries;

        try {
            client = lpServerConnect(connectLP);
            scheduledQueries = client.listScheduledQueries();
            if (scheduledQueries.size() == 0) {
                printStackTrace();
                client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.SCHEDULED_QUERIES_NOT_FOUND.toString() + " 예약쿼리 전체 조회 중 예외 발생하였습니다"));
                throw new NoObjectFoundException(ErrorCode.SCHEDULED_QUERIES_NOT_FOUND, "예약쿼리 전체 조회 중 예외 발생하였습니다", connectLP);
            }
        } catch (IOException e) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 등록된 예약쿼리가 없습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "등록된 예약쿼리가 없습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return scheduledQueries;
    }

    /**
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @param scheduled
     * @return
     */
    public String createOrUpdateScheduledQuery(final String connectLP, final ScheduledQuery scheduled) {
        try {
            client = lpServerConnect(connectLP);
            List<ScheduledQuery> list = client.listScheduledQueries();
            final String name = scheduled.getTitle();

            ScheduledQuery result = list.stream()
                    .filter(scheduledQuery -> scheduledQuery.getTitle().equals(name))
                    .findFirst().orElse(null);
            if (result != null) {
                scheduled.setGuid(result.getGuid());
                client.updateScheduledQuery(scheduled);
                log.info("Update Success : [scheduled query] {} - scheduled query updated", name);
            } else {
                client.createScheduledQuery(scheduled);
                log.info("Create Success : [scheduled query] {} - scheduled query created", name);
            }
            return name;

        } catch (IOException | MessageException ioe) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(SCHEDULED_QUERY, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 예약쿼리 생성/조회 중 예외 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "예약쿼리 생성/조회 중 예외 발생하였습니다", connectLP, ioe);
        } finally {
            lpServerClose();
        }
    }

    /**
     * 해당 Logpresso에서 guid 를 조건으로 그룹 정보를 조회한다.
     * by magnaru
     *
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @param guid      : Logpresso에서 사용하는 보안그룹 guid
     * @return 보안그룹 정보 반환
     */
    public SecurityGroup getSecurityGroupFromGuid(final String connectLP, final String guid) {
        SecurityGroup securityGroup;
        try {
            client = lpServerConnect(connectLP);
            securityGroup = client.getSecurityGroup(guid);
        } catch (IOException | MessageException ioe) {
            // 2023-03-31 TARGET_LP에서만 백업을 진행하므로 TARGET_LP를 넣어두었다.
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(PROCEDURE, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 보안 그룹 정보 조회 중 예외 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "보안 그룹 정보 조회 중 예외 발생하였습니다");
        } finally {
            lpServerClose();
        }
        return securityGroup;
    }

    /**
     * @param connectLP          접속대상 Lopgresso 서버 (source 또는 target)
     * @param procedureToRestore
     * @param objectType
     * @return
     */
    public List<Map<String, Object>> getRestoreData(final String connectLP, final String procedureToRestore, final String objectType,
                                                    final DateTime from, final DateTime to) {
        Map<String, String> values = new HashMap<>();
        values.put("name", procedureToRestore);
        values.put("object_type", objectType);
        values.put("backup_table", backupTable);
        if (objectType.equals(PROCEDURE) || objectType.equals(STREAM_QUERY)) {
            values.put("search_name", "name");
        } else if (objectType.equals(SCHEDULED_QUERY)) {
            values.put("search_name", "title");
        }
        values.put("from", from.toString(yyyyMMddHHmmss));
        values.put("to", to.toString(yyyyMMddHHmmss));

        final String query = replace(
                "# deploy-restore_${object_type}_${name} " +
                        " | table from=${from} to=${to} ${backup_table} " +
                        " | search ${search_name} == \"${name}\" and object_type==\"${object_type}\"" +
                        " | sort limit=1 -_time ", values);

        return simpleQuery(connectLP, query);
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
            data.append(getSecurityGroupFromGuid(TARGET_LP, group).getName());
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

    /**
     * @param procedure
     * @return
     */
    protected String convertExcludeSyntax(final Procedure procedure) {
        StringBuilder str = new StringBuilder(procedure.getQueryString());
        str.replace(indexOf(procedure.getQueryString(), "| # {"), indexOf(procedure.getQueryString(), "| # }") + 5, "");
        return str.toString();
    }

    public List callProcToChangeList(final String connectLP, final String objectType, final DateTime from) {
        Map<String, String> values = new HashMap<>();
        values.put("object_type", objectType);
        values.put("from", from.toString(yyyyMMddHHmmss));

        final String query = replace(
                "# deploy-change_list_${object_type}" +
                        " | proc 99_change_list(\"${from}\")" +
                        " | search contains(lower(type), lower(\"${object_type}\"))" +
                        " | fields type, name, history" +
                        " | eval name = split(name, \"\\n\")" +
                        " | explode name" +
                        " | stats max(history) as history by type, name" +
                        " | search lower(name) != \"cep_metric*\" and lower(name) != \"cep_apdex*\" and lower(name) != \"cep_event*\" and name != \"table_backup\"" +
                        "   and history != \"*removeProcedures\" and history != \"*removeScheduledQuery\" and history != \"*removeStreamQueries\"" +
                        " | eval type = case( type==\"ProcedurePlugin\", \"procedureList\", type==\"ScheduledQueryPlugin\", \"scheduleList\", type==\"StreamQueryPlugin\", \"streamList\", type)" +
                        " | fields type, name", values);

        List names = new ArrayList();
        final List<Map<String, Object>> changeList = simpleQuery(connectLP, query);
        IntStream.range(0, changeList.size()).forEach(i -> {
            names.add(changeList.get(i).get("name"));
        });
        return names;
    }

    public List<Tuple> getErrorHistoryInsertTuple(final String objectType, final String method,
                                                  final String errorMsg) {
        LocalDateTime excuteTime = LocalDateTime.now();

        List<Tuple> result = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();

        map.put("_time", Date.from((excuteTime.atZone(ZoneId.systemDefault()).toInstant())));
        map.put("time", excuteTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        map.put("object_type", objectType);
        map.put("success", false);
        map.put("method", method);
        map.put("error_message", errorMsg);

        Tuple tuple = new Tuple(map);
        result.add(tuple);
        return result;
    }

    /**
     * 한 개의 스트림쿼리를 조회하여 logpresso 테이블에 백업을 위해 Insert 한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param connectLP    접속대상 Lopgresso 서버 (source 또는 target)
     * @param streamToFind 백업 대상 프로시저명
     * @return insert 건수
     */
    public int insertLPTableStreamQuery(final String connectLP, final String streamToFind) {
        int result = 0;

        try {
            client = lpServerConnect(connectLP);
            getTableSchema(client, backupTable);

            List<StreamQueryStatus> streamQueryStatuses = client.listStreamQueries();
            StreamQueryStatus streamStatus = streamQueryStatuses.stream()
                    .filter(streamQuery -> streamQuery.getStreamQuery().getName().equals(streamToFind))
                    .findFirst().orElse(null);

            if (null != streamStatus) {
                StreamQuery streamInfo = streamStatus.getStreamQuery();

                List<Tuple> rows = new ArrayList<>();
                Tuple cols = new Tuple();

                // required
                cols.put("object_type", STREAM_QUERY);
                cols.put("name", streamInfo.getName());
                cols.put("interval", streamInfo.getInterval());
                cols.put("source_type", streamInfo.getSourceType());
                cols.put("sources", streamInfo.getSources());
                cols.put("owner", streamInfo.getOwner());
                cols.put("is_enabled", streamInfo.isEnabled());
                cols.put("is_async", "수동확인");   //boolean (default:false)
                cols.put("query", streamInfo.getQueryString());
                // optional
                cols.put("description", streamInfo.getDescription());

//            rows.add(cols);           // 다수 행을 저장할때 List 로 처리

                Future<Integer> insertRow = client.insert(backupTable, cols);
                if (insertRow.isDone() && 0 <= insertRow.get()) {
                    throw new LogpressoException(ErrorCode.DATA_NOT_INSERTED_EXCEPTION, connectLP);
                }
                ++result;
                log.info("LP Table Backup Success : [{}] {} - inserted ", STREAM_QUERY, cols.get("name"));
            } else {
                log.info("{} 로그프레소에서 {}를 찾을 수 없어 백업하지 않았습니다", connectLP, streamToFind);
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.LP_INSERT_EXCEPTION.toString() + " 백업 중 예외가 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_INSERT_EXCEPTION, streamToFind + " 백업 중 예외가 발생하였습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return result;
    }

    /**
     * 전체 스트림쿼리를 조회하여 logpresso 테이블에 백업을 위해 Insert 한다.
     * 백업용 테이블이 없거나 사용 권한이 없으면 새로 생성한다.
     *
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @return insert 건수
     */
    public int insertLPTableStreamQueryAll(final String connectLP) {
        int result = 0;

        List<Tuple> rows = new ArrayList<>();
        Tuple cols;

        try {
            client = lpServerConnect(connectLP);
            getTableSchema(client, backupTable);

            List<StreamQueryStatus> allStreams = client.listStreamQueries();

            if (allStreams.size() != 0) {
                for (StreamQueryStatus stream : allStreams) {
                    cols = new Tuple();
                    // required
                    cols.put("object_type", STREAM_QUERY);
                    cols.put("name", stream.getStreamQuery().getName());
                    cols.put("interval", stream.getStreamQuery().getInterval());
                    cols.put("source_type", stream.getStreamQuery().getSourceType());
                    cols.put("sources", stream.getStreamQuery().getSources());
                    cols.put("owner", stream.getStreamQuery().getOwner());
                    cols.put("is_enabled", stream.getStreamQuery().isEnabled());
                    cols.put("query", stream.getStreamQuery().getQueryString());
                    // optional
                    cols.put("is_async", "수동확인");   //boolean (default:false)
                    cols.put("description", stream.getStreamQuery().getDescription());

//                rows.add(cols);           // 다수 행을 저장할때 List 로 처리

                    Future<Integer> insertRow = client.insert(backupTable, cols);
                    if (insertRow.isDone() && 0 <= insertRow.get()) {
                        client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.DATA_NOT_INSERTED_EXCEPTION.toString()));
                        throw new LogpressoException(ErrorCode.DATA_NOT_INSERTED_EXCEPTION, connectLP);
                    }
                    ++result;
                    log.info("LP Table Backup Success : [{}] {} - inserted ", STREAM_QUERY, cols.get("name"));
                }
            } else {
                printStackTrace();
                client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.STREAM_QUERIES_NOT_FOUND.toString() + " 백업 과정에서 예외가 발생하였습니다"));
                throw new NoObjectFoundException(ErrorCode.STREAM_QUERIES_NOT_FOUND, "백업 과정에서 예외가 발생하였습니다", connectLP);
            }
            log.info("[{}] {} 건 insert 처리 완료", STREAM_QUERY, result);
        } catch (InterruptedException | ExecutionException | IOException e) {
            printStackTrace();
            final String msg = String.format("%s건까지 처리 완료", result);
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.LP_INSERT_EXCEPTION.toString()));
            throw new LogpressoException(ErrorCode.LP_INSERT_EXCEPTION, msg, connectLP, e);
        } finally {
            lpServerClose();
        }
        return result;
    }

    /**
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @param stream
     * @return
     */
    public String createOrUpdateStream(final String connectLP, final StreamQuery stream) {
        try {
            client = lpServerConnect(connectLP);
            final List<StreamQueryStatus> list = client.listStreamQueries();
            final String name = stream.getName();

            StreamQueryStatus result = list.stream()
                    .filter(streamQuery -> streamQuery.getStreamQuery().getName().equals(name))
                    .findFirst().orElse(null);
            if (result != null) {
                client.updateStreamQuery(stream);
                log.info("Update Success : [stream query] {} - stream query updated", name);
            } else {
                client.createStreamQuery(stream);
                log.info("Create Success : [stream query] {} - stream query created", name);
            }
            return name;

        } catch (IOException | MessageException ioe) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 스트림쿼리 생성/수정 중 예외 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "스트림쿼리 생성/수정 중 예외 발생하였습니다", connectLP, ioe);
        } finally {
            lpServerClose();
        }
    }

    public DeployExecutionResult createOrUpdateStreamBatch(final String connectLP, final List<StreamQueryStatus> sourceStreamQueries) {
        List<String> createdList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        try {
            client = lpServerConnect(connectLP);

            List<StreamQueryStatus> targetList = client.listStreamQueries();

            for (StreamQueryStatus sourceStreamQuery : sourceStreamQueries) {
                final String name = sourceStreamQuery.getStreamQuery().getName();
                StreamQueryStatus result = targetList.stream()
                        .filter(streamQ -> streamQ.getStreamQuery().getName().equals(name))
                        .findFirst().orElse(null);
                try {
                    if (result != null) {
                        client.updateStreamQuery(sourceStreamQuery.getStreamQuery());
                    } else {
                        client.createStreamQuery(sourceStreamQuery.getStreamQuery());
                    }
                    createdList.add(name);
                } catch (MessageException | IOException e) {
                    failedList.add(name);
                }
            }
        } catch (Exception e) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 스트림쿼리 일괄 생성/수정 중 예외 발생하였습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "스트림쿼리 일괄 생성/수정 중 예외 발생하였습니다", connectLP, e);
        } finally {
            lpServerClose();
        }
        return new DeployExecutionResult(createdList, failedList);
    }

    /**
     * @param connectLP 접속대상 Lopgresso 서버 (source 또는 target)
     * @return 스트림쿼리 목록
     */
    public List<StreamQueryStatus> getStreamQueries(final String connectLP) {
        List<StreamQueryStatus> streamQueryStatuses = new ArrayList<>();

        try {
            client = lpServerConnect(connectLP);
            streamQueryStatuses = client.listStreamQueries();
            if (streamQueryStatuses.size() == 0) {
                printStackTrace();
                client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.STREAM_QUERIES_NOT_FOUND.toString() + " 스트림쿼리 전체 조회 중 예외 발생하였습니다"));
                throw new NoObjectFoundException(ErrorCode.STREAM_QUERIES_NOT_FOUND, "스트림쿼리 전체 조회 중 예외 발생하였습니다", connectLP);
            }
        } catch (IOException ioe) {
            printStackTrace();
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.LP_ELEMENTS_EXCEPTION.toString() + " 등록된 스트림쿼리가 없습니다"));
            throw new LogpressoException(ErrorCode.LP_ELEMENTS_EXCEPTION, "등록된 스트림쿼리가 없습니다", connectLP, ioe);
        } finally {
            lpServerClose();
        }
        return streamQueryStatuses;
    }

    /**
     * @param connectLP    접속대상 Lopgresso 서버 (source 또는 target)
     * @param streamToFind
     * @return
     */
    public StreamQueryStatus getStreamQuery(final String connectLP, final String streamToFind) {
        StreamQueryStatus stream = null;
        final List<StreamQueryStatus> streamQueryStatuses = getStreamQueries(connectLP);
        stream = streamQueryStatuses.stream()
                .filter(streamQuery -> streamQuery.getStreamQuery().getName().equals(streamToFind))
                .findFirst().orElse(null);
        if (stream == null) {
            printStackTrace();
            client = lpServerConnect(TARGET_LP);
            client.insert(deployHistoryTable, getErrorHistoryInsertTuple(STREAM_QUERY, getStackTrace(), ErrorCode.STREAM_QUERY_NOT_FOUND + " 스트림쿼리가 없습니다"));
            lpServerClose();
            throw new NoObjectFoundException(ErrorCode.STREAM_QUERY_NOT_FOUND, "스트림쿼리가 없습니다", connectLP, streamToFind);
        }
        return stream;
    }
    public List<String> getLocalLoggers() {
        List<String> fullNames = new ArrayList<>();

        final Map<String, Object> getLoggerConfig = new HashMap<>();
        getLoggerConfig.put("locale", "ko");

        try (final Logpresso client = new Logpresso()) {
            client.connect(sourceHost, sourcePort, sourceUser, sourcePassword);
            Map<String, Object> loggerList = getListLocalLoggers(client);
            List<Map<String, String>> loggers = (List<Map<String, String>>) loggerList.get("loggers");
            loggers.forEach(logger -> fullNames.add(logger.get("fullname")));
        } catch (IOException ioe) {
            log.error(ioe);
        }
        return fullNames;
    }
    public Map<String, Object> getLocalLoggerConfig(final String loggerToFind) {

        List<String> fullNames = new ArrayList<>();
        Map<String,Object> config = new HashMap<>();

        final Map<String, Object> getLoggerConfig = new HashMap<>();
        getLoggerConfig.put("locale", "ko");

        try (final Logpresso client = new Logpresso()) {
            client.connect(sourceHost, sourcePort, sourceUser, sourcePassword);
            Map<String, Object> loggerList = getListLocalLoggers(client);
            List<Map<String, String>> loggers = (List<Map<String, String>>) loggerList.get("loggers");
            loggers.forEach(logger -> fullNames.add(logger.get("fullname")));

            final String source = fullNames.stream().filter(fullName -> fullName.equals("local\\" + loggerToFind)).findFirst().orElse("");
            if (!source.equals("")) {
                getLoggerConfig.put("logger_fullname", source);
                config=(Map<String, Object>)client.getSession()
                        .rpc(LOGGER_INFO, getLoggerConfig)
                        .getParameters()
                        .get("logger");
            } else {
                throw new RuntimeException(String.format("no logger with name %s exist", loggerToFind));
            }
        } catch (IOException ioe) {
            log.error(ioe);
        }
        return config;
    }

    public Map<String, Object> getListLocalLoggers(final Logpresso client) {
        Map<String, Object> listLoggers = new HashMap<>();
        listLoggers.put("offset", 0);
        listLoggers.put("keywords", "");
        listLoggers.put("limit", 500);
        listLoggers.put("locale", "ko");
        listLoggers.put("logger_type", "local");
        try {
            return client.getSession().rpc(LOGGERS_LIST, listLoggers).getParameters();
        } catch (IOException ioe) {
            throw new RuntimeException("error while getting list local logger");
        }
    }

    public boolean createOrUpdateLocalLogger(final String connectLP, final Map<String, Object> config, final String loggerToCreate) {
        final Map<String, Object> createLocalLogger = new HashMap<>();
        try {
            client = lpServerConnect(connectLP);
            final Logger a = client.getLogger("local\\" + loggerToCreate);
            if(a != null && a.getName().equals(loggerToCreate)) {
                stopAndDeleteLocalLogger(loggerToCreate);
            }
            createLocalLogger.put("configs", config.get("configs"));
            createLocalLogger.put("host_tag", config.get("host_tag"));
            createLocalLogger.put("factory_name", config.get("factory_name"));
            createLocalLogger.put("logger_name", loggerToCreate);
            createLocalLogger.put("table_name", config.get("table"));

            client.getSession().rpc(CREATE_LOCAL_LOGGER, createLocalLogger);
            log.info("logger {} created", loggerToCreate);
        } catch (IOException ioe) {
            throw new RuntimeException("error while creating local logger");
        }
        return true;
    }
    public void stopAndDeleteLocalLogger(final String loggerName) {
        final Map<String ,Object> delete = new HashMap<>();
        delete.put("logger_name", loggerName);
        try {
            client.getSession().rpc(STOP_LOCAL_LOGGER, delete);
            delete.remove("logger_name");
            delete.put("logger_names", Collections.singletonList(loggerName));
            client.getSession().rpc(REMOVE_LOCAL_LOGGERS, delete);
            log.info("logger {} removed", loggerName);
        } catch (IOException ioe) {
            final String message = String.format("error occurred while stopping/deleting local logger [%s]", loggerName);
            throw new RuntimeException(message);
        }
    }
    public String includeWarning(final String queryString) {
        return "json \"{lp_proc_status :\\\"확인필요\\\"}\"\n|# [ " + queryString;
    }
}
