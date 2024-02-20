package com.exem.xaiops.autodeployer.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
public class AdminSystem implements Serializable {
    private int sys_id;
    private String name;
    private String type;
}
