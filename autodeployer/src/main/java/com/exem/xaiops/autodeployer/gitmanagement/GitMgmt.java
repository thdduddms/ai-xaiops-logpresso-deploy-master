package com.exem.xaiops.autodeployer.gitmanagement;

import com.exem.xaiops.autodeployer.config.logpresso.LogpressoClientGit;
import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.List;

public abstract class GitMgmt<T> {

    protected final String path;
    protected final LogpressoClientGit lPClient;
    public GitMgmt(final LogpressoClientGit lPClient, @Value("${git.path.textfile}") final String path) {
        this.lPClient = lPClient;
        this.path = path;
    }

    public abstract DeployMapper getMapper();
    public abstract List<T> createTxtFiles(final String connectLP);
    public abstract T generateFile(final String connectLP, final T thing);
    public abstract T generateFileByName(final String connectLP, final String nameToFind);
    protected boolean makeDirectoryIfAbsent(String dirPath) {
        File file = new File(dirPath);
        boolean created = false;
        if(!file.exists()) {
            created = file.mkdirs();
        }
        return created;
    }
}
