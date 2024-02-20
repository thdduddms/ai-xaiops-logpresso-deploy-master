package com.exem.xaiops.autodeployer.gitmanagement.impl;

import com.exem.xaiops.autodeployer.Constant;
import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClientGit;
import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import com.exem.xaiops.autodeployer.gitmanagement.GitMgmt;
import com.logpresso.client.ScheduledQuery;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
@Component
@Log4j2
public class GitScheduled extends GitMgmt<ScheduledQuery> {
    final String pathDev;
    final String pathProd;
    public GitScheduled(final LogpressoClientGit lPClient, @Value("${git.path.textfile}") final String path) {
        super(lPClient, path);
        pathDev = String.format("%s%s/%s/", path, Constant.GIT_SOURCE_LP ,Constant.SCHEDULED_QUERY);
        pathProd = String.format("%s%s/%s/", path, Constant.GIT_TARGET_LP, Constant.SCHEDULED_QUERY);

    }
    @Override
    public DeployMapper getMapper() {
        return DeployMapper.SCHEDULES;
    }

    @Override
    public List<ScheduledQuery> createTxtFiles(final String connectLP) {
        final String pathScheduled = connectLP.equals(Constant.SOURCE_LP) ? pathDev : pathProd;
        final boolean created = makeDirectoryIfAbsent(pathScheduled);
        if(created) { log.info("directory created"); }
        List<ScheduledQuery> schedules = lPClient.getScheduledQueries(connectLP).stream()
                .filter(schedule -> !schedule.getTitle().contains("test"))
                .filter(schedule -> !schedule.getTitle().contains("temp"))
                .collect(Collectors.toList());
        schedules.forEach(each -> generateFile(pathScheduled, each));
        log.info("created scheduled queries:: path : \"{}\" count : {}" ,pathScheduled, schedules.size());

        return schedules;
    }

    @Override
    public ScheduledQuery generateFile(final String pathScheduled, final ScheduledQuery scheduled) {
        final StringBuilder builder = new StringBuilder();
        final String name = scheduled.getTitle();
        final String cron = scheduled.getCronSchedule();
        final String queryString = scheduled.getQueryString();
        final String alert = scheduled.getAlertQuery();

        final String fullName = builder.append(pathScheduled).append(name)
                .append(".txt").toString();

        try {
            builder.setLength(0);
            final String text = builder
                    .append("실행 주기 : ")
                    .append(cron)
                    .append("\n경보 사용 : ")
                    .append(alert)
                    .append("\n\n")
                    .append(queryString).toString();

            File file = new File(fullName);
            FileWriter writer = new FileWriter(file, false);

            writer.write(text);
            writer.flush();
            writer.close();

            log.info("text file created - {}", name);
        } catch (NullPointerException npe) {
            throw new RuntimeException("data does not exist");
        } catch (IOException ioe) {
            log.error(ioe);
        }
        return scheduled;
    }

    @Override
    public ScheduledQuery generateFileByName(final String connectLP, final String nameToFind) {
        final String pathScheduled = connectLP.equals(Constant.SOURCE_LP) ? pathDev : pathProd;
        makeDirectoryIfAbsent(pathScheduled);
        return generateFile(pathScheduled, lPClient.getScheduledQuery(connectLP, nameToFind));
    }
}
