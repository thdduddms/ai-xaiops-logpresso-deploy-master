package com.exem.xaiops.autodeployer.deploy;

import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClient;
import com.exem.xaiops.autodeployer.vo.DeployExecutionResult;
import com.logpresso.client.Tuple;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.exem.xaiops.autodeployer.Constant.TARGET_LP;

public abstract class Deploy<T> {
    public abstract DeployMapper getMapper();
    protected final LogpressoClient lPClient;

    @Value("${logpresso.target.history_table_name}")
    private String deployHistoryTable;

    public Deploy(final LogpressoClient lPClient) {
        this.lPClient=lPClient;
    }

    public abstract boolean fetchAndDeploy(final String thingToFind, final String thingToCreate);
    public abstract boolean findAndBackup(final String thingToFind);
    public abstract boolean findAndBackupAll();
    public abstract List findAndDeployCallProc(final DateTime from);
    public abstract String restoreOneFromTargetLP(final String thingToRestore, final DateTime from, final DateTime to);
    public abstract DeployExecutionResult restoreMultiFromTargetLP(final List<String> thingToRestore, final DateTime from, final DateTime to);
    public abstract DeployExecutionResult fetchAndDeployBatch();
    public abstract DeployExecutionResult fetchAndDeployMulti(final List<String> thingsToCreate);

    public void deployHistoryInsert(final String objectType, final Object list, final String ip, final String url, final boolean success, final String errorMsg) throws IOException {
        LocalDateTime excuteTime  = LocalDateTime.now();

        List<Tuple> result = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();

        map.put("_time", Date.from((excuteTime.atZone(ZoneId.systemDefault()).toInstant())));
        map.put("time", excuteTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        map.put("object_type", objectType);
        map.put("client_ip", ip);
        map.put("api_url", url);
        map.put("list", list);
        map.put("success", success);
        map.put("error_message", errorMsg);

        Tuple tuple = new Tuple(map);
        result.add(tuple);

        lPClient.insertLP(TARGET_LP, deployHistoryTable, result);
    }

}
