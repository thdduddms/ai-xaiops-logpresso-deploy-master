package com.exem.xaiops.autodeployer.vo;

import lombok.Getter;

import java.util.List;

@Getter
public class DeployExecutionResult {
    public DeployExecutionResult(final List<String> successfulList, final List<String> failedList) {
        this.failedList=failedList;
        this.successfulList=successfulList;
    }
    final private List<String> successfulList;
    final private List<String> failedList;
}
