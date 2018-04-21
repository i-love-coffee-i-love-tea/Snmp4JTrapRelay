package org.gobuki.net.snmp.traprelay;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.smi.VariableBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

public class TrapEventJsonConverter implements TrapEventConverter<String> {

    SimpleDateFormat sdf;

    public TrapEventJsonConverter() {
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    }

    public String convertTrap(CommandResponderEvent event) {

        StringBuilder msg = new StringBuilder();
        // Json encoder for string escaping
        JsonStringEncoder jsonEncoder = new JsonStringEncoder();

        // Host address of the trap originator
        msg.append("JSONTRAP:{ \"trapSrc\": \"");
        msg.append(event.getPeerAddress());

        msg.append("\", \"timestamp\": \"");
        msg.append(sdf.format(new Date()));

        msg.append("\", \"secLevel\": \"");
        msg.append(event.getSecurityLevel());

        msg.append("\", \"secModel\": \"");
        msg.append(event.getSecurityModel());

        msg.append("\", \"secName\": \"");
        jsonEncoder.quoteAsString(new String(event.getSecurityName()), msg);

        msg.append("\", \"variables\": { ");

        // Var binds
        Vector<? extends VariableBinding> varBinds = event.getPDU().getVariableBindings();
        if (varBinds != null && !varBinds.isEmpty()) {
            Iterator<? extends VariableBinding> varIter = varBinds.iterator();
            while (varIter.hasNext()) {
                VariableBinding var = varIter.next();
                msg.append("\"").append(var.getOid().format()).append("\": \"");
                jsonEncoder.quoteAsString(var.toValueString(), msg);
                msg.append("\"");

                if (varIter.hasNext()) {
                    msg.append(", ");
                }
            }
        }

        msg.append(" } }");

        return msg.toString();
    }
}
