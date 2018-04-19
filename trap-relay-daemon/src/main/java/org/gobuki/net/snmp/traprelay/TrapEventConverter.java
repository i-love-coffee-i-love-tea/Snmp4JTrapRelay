package org.gobuki.net.snmp.traprelay;

import org.snmp4j.CommandResponderEvent;

public interface TrapEventConverter<T> {

    public T convertTrap(CommandResponderEvent event);
}
