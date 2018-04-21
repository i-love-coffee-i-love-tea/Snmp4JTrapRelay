package org.gobuki.net.snmp.traprelay;

import org.snmp4j.CommandResponderEvent;

public interface TrapEventHandler {

    public void handleTrapEvent(CommandResponderEvent event);
}
