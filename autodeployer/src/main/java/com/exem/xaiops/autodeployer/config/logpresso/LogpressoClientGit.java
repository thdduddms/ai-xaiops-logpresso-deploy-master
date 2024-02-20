package com.exem.xaiops.autodeployer.config.logpresso;

import com.logpresso.client.Procedure;
import com.logpresso.client.ScheduledQuery;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Log4j2
@Component
public class LogpressoClientGit {
    private final LogpressoClient lPClient;
    private final String sourceHost;
    private final int sourcePort;
    private final String sourceUser;
    private final String sourcePassword;
    private final String targetHost;
    private final int targetPort;
    private final String targetUser;
    private final String targetPassword;

    public LogpressoClientGit(@Value("${git.logpresso.source.host}") final String sourceHost, @Value("${git.logpresso.source.port}") final int sourcePort,
                              @Value("${git.logpresso.source.user}") final String sourceUser, @Value("${git.logpresso.source.password}") final String sourcePassword,
                              @Value("${git.logpresso.target.host}") final String targetHost, @Value("${git.logpresso.target.port}") final int targetPort,
                              @Value("${git.logpresso.target.user}") final String targetUser, @Value("${git.logpresso.target.password}") final String targetPassword) {
        this.sourceHost = sourceHost;
        this.sourcePort = sourcePort;
        this.sourceUser = sourceUser;
        this.sourcePassword = sourcePassword;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.targetUser = targetUser;
        this.targetPassword = targetPassword;
        lPClient = new LogpressoClient(sourceHost, sourcePort, sourceUser, sourcePassword, targetHost, targetPort, targetUser, targetPassword);
    }

    public List<Procedure> getProcedures(final String connectLP) {
        return lPClient.getProcedures(connectLP);
    }

    public Procedure getProcedure(final String connectLP, final String nameToFind) {
        return lPClient.getProcedure(connectLP, nameToFind);
    }

    public List<ScheduledQuery> getScheduledQueries(final String connectLP) {
        return lPClient.getScheduledQueries(connectLP);
    }
    public ScheduledQuery getScheduledQuery(final String connectLP, final String nameToFind) {
        return lPClient.getScheduledQuery(connectLP, nameToFind);
    }
}
