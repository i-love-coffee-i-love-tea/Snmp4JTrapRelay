package org.gobuki.net.snmp.traprelay.handler;

public class SimpleLoggingTrapHandler implements TrapHandler {

    @Override
    public void handleTrap(String strTrapInfo) {
        System.out.println(strTrapInfo);
    }
}
