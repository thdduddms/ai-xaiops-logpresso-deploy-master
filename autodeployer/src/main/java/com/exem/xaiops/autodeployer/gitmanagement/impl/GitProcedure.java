package com.exem.xaiops.autodeployer.gitmanagement.impl;

import com.exem.xaiops.autodeployer.Constant;
import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClientGit;
import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import com.exem.xaiops.autodeployer.gitmanagement.GitMgmt;
import com.logpresso.client.Procedure;
import com.logpresso.client.ProcedureParameter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
@Component
@Log4j2
public class GitProcedure extends GitMgmt<Procedure> {

    final String pathDev;
    final String pathProd;

    public GitProcedure(final LogpressoClientGit lPClient, @Value("${git.path.textfile}") final String path) {
        super(lPClient, path);
        pathDev = String.format("%s%s/%s/", path, Constant.GIT_SOURCE_LP ,Constant.PROCEDURE);
        pathProd = String.format("%s%s/%s/", path, Constant.GIT_TARGET_LP, Constant.PROCEDURE);
    }

    @Override
    public DeployMapper getMapper() {
        return DeployMapper.PROCEDURES;
    }

    @Override
    public List<Procedure> createTxtFiles(final String connectLP) {
        final String pathProc = connectLP.equals(Constant.SOURCE_LP) ? pathDev : pathProd;
        final boolean created = makeDirectoryIfAbsent(pathProc);
        if (created) { log.info("directory created"); }
        List<Procedure> results = new ArrayList<>();
        lPClient.getProcedures(connectLP).stream()
                .filter(proc -> !proc.getName().contains("test"))
                .filter(proc -> !proc.getName().contains("temp"))
                .forEach(each -> results.add(generateFile(pathProc, each)));

        log.info("created procedures:: path : \"{}\" count : {}", pathProc, results.size());

        return results;
    }

    @Override
    public Procedure generateFile(final String pathProc, final Procedure procedure) {
        final StringBuilder builder = new StringBuilder();
        final String name = procedure.getName();
        final String desc = procedure.getDescription();
        final String queryString = procedure.getQueryString();
        final List<ProcedureParameter> params = procedure.getParameters();

        final String fullName = builder.append(pathProc).append(name)
                .append(".txt").toString();

        try {
            builder.setLength(0);
            final String text = builder
                    .append("파라미터 : ")
                    .append(params)
                    .append("\n설명 : ")
                    .append(desc)
                    .append("\n\n")
                    .append(queryString).toString();

            File file = new File(fullName);
            FileWriter writer = new FileWriter(file, false);

            writer.write(text);
            writer.flush();
            writer.close();

            log.info("text file created - {}", name);
        } catch (IOException ioe) {
            log.error(ioe);
        }
        return procedure;
    }

    @Override
    public Procedure generateFileByName(final String connectLP, final String nameToFind) {
        final String pathProc = connectLP.equals(Constant.SOURCE_LP) ? pathDev : pathProd;
        makeDirectoryIfAbsent(pathProc);
        return generateFile(pathProc, lPClient.getProcedure(connectLP, nameToFind));
    }
}
