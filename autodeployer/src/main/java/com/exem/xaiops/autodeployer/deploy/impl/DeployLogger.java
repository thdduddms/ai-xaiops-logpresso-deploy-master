package com.exem.xaiops.autodeployer.deploy.impl;

import com.exem.xaiops.autodeployer.Constant;
import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClient;
import com.exem.xaiops.autodeployer.deploy.Deploy;
import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import com.exem.xaiops.autodeployer.vo.DeployExecutionResult;
import com.logpresso.client.Logger;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.exem.xaiops.autodeployer.Constant.TARGET_LP;

@Component
@Log4j2
public class DeployLogger extends Deploy<Logger> {
    public DeployLogger(LogpressoClient lPClient) {
        super(lPClient);
    }

    @Override
    public DeployMapper getMapper() {
        return DeployMapper.LOGGERS;
    }

    @Override
    public boolean fetchAndDeploy(String thingToFind, String thingToCreate) {
        final Map<String, Object> config =  lPClient.getLocalLoggerConfig(thingToFind);
        return lPClient.createOrUpdateLocalLogger(TARGET_LP, config, thingToCreate);
    }

    @Override
    public boolean findAndBackup(String thingToFind) {
        return false;
    }

    @Override
    public boolean findAndBackupAll() {
        return false;
    }

    @Override
    public List findAndDeployCallProc(DateTime from) {
        return null;
    }

    @Override
    public String restoreOneFromTargetLP(String thingToRestore, DateTime from, DateTime to) {
        return null;
    }

    @Override
    public DeployExecutionResult restoreMultiFromTargetLP(List<String> thingToRestore, DateTime from, DateTime to) {
        return null;
    }

    @Override
    public DeployExecutionResult fetchAndDeployBatch() {
        return null;
    }

    @Override
    public DeployExecutionResult fetchAndDeployMulti(List<String> thingsToCreate) { return new DeployExecutionResult(new ArrayList<>(), new ArrayList<>()); }
}
