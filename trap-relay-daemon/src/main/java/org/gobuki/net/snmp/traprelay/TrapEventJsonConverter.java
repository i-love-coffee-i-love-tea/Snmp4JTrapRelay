package org.gobuki.net.snmp.traprelay;

import org.snmp4j.CommandResponderEvent;
import org.snmp4j.smi.VariableBinding;

import java.util.Iterator;
import java.util.Vector;

public class TrapEventJsonConverter implements TrapEventConverter<String> {

    public String convertTrap(CommandResponderEvent event) {

        StringBuffer msg = new StringBuffer();

        // Trap source host address
        msg.append("{ \"trapSrc\": \"");
        msg.append(event.getPeerAddress());

        msg.append("\", \"secLevel\": \"");
        msg.append(event.getSecurityLevel());

        msg.append("\", \"secModel\": \"");
        msg.append(event.getSecurityModel());

        msg.append("\", \"secName\": \"");
        msg.append(new String(event.getSecurityName()));

        msg.append("\", \"variables\": { ");

        // Var binds
        Vector<? extends VariableBinding> varBinds = event.getPDU().getVariableBindings();
        if (varBinds != null && !varBinds.isEmpty()) {
            Iterator<? extends VariableBinding> varIter = varBinds.iterator();
            while (varIter.hasNext()) {
                VariableBinding var = varIter.next();
                msg.append("\"").append(var.getOid().format()).append("\": \"" + var.toValueString()).append("\"");

                if (varIter.hasNext()) {
                    msg.append(", ");
                }
            }
        }

        msg.append(" } }");

        return msg.toString();
    }

    private String getAssocArrayElement(String name, String value) {
        return "";
    }
}
