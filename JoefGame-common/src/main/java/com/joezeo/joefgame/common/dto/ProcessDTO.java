package com.joezeo.joefgame.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProcessDTO implements Serializable {

    private static final long serialVersionUID = -7233337218914167212L;

    private String id;
    private String name;
    private Integer version;
    private String key;

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
