package org.gobuki.net.snmp.traprelay.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gobuki.net.snmp.traprelay.JsonTrapInfo;

import java.io.IOException;

public class JsonObjectTrapHandler implements TrapHandler {

    ObjectMapper mapper;

    public JsonObjectTrapHandler() {
        mapper = new ObjectMapper();
    }

    @Override
    public void handleTrap(String strJsonTrapInfo) {
        if (strJsonTrapInfo.startsWith("JSONTRAP:")) {
            strJsonTrapInfo = strJsonTrapInfo.substring(9, strJsonTrapInfo.length());



            try {
                JsonTrapInfo trapInfo = mapper.readValue(strJsonTrapInfo, JsonTrapInfo.class);

                System.out.println("Trap source: " + trapInfo.getTrapSrc());
                for (String strOid : trapInfo.getVariables().keySet()) {
                    System.out.println("\t" + strOid + ": " + trapInfo.getVariables().get(strOid));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // not a json trap
        }
    }
}
