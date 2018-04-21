package org.gobuki.net.snmp.traprelay;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JsonTrapInfo {

    private Date timestamp;
    private String trapSrc;
    private String secLevel;
    private String secModel;
    private String secName;
    private Map<String, String> variables;

    public JsonTrapInfo() {
        variables = new HashMap<String, String>();
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getTrapSrc() {
        return trapSrc;
    }

    public void setTrapSrc(String trapSrc) {
        this.trapSrc = trapSrc;
    }

    public String getSecLevel() {
        return secLevel;
    }

    public void setSecLevel(String secLevel) {
        this.secLevel = secLevel;
    }

    public String getSecModel() {
        return secModel;
    }

    public void setSecModel(String secModel) {
        this.secModel = secModel;
    }

    public String getSecName() {
        return secName;
    }

    public void setSecName(String secName) {
        this.secName = secName;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }
}
