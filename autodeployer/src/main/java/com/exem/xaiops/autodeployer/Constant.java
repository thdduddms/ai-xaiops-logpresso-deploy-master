package com.exem.xaiops.autodeployer;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Constant {
    public static final DateTimeFormatter summaryFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter yyyyMMddHHmmss = DateTimeFormat.forPattern("yyyyMMddHHmmss");
    public static final String PROCEDURE = "procedure";
    public static final String SCHEDULED_QUERY = "schedule";
    public static final String STREAM_QUERY = "stream";
    public static final String LOCAL_LOGGER = "logger";
    public static final String LOOKUP_TABLE="lookup";

    public static final String SOURCE_LP = "source";
    public static final String TARGET_LP = "target";
    public static final String GIT_SOURCE_LP = "dev";
    public static final String GIT_TARGET_LP = "prd";
}
