package org.gobuki.net.snmp.traprelay;

import org.snmp4j.*;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TrapListener implements CommandResponder {

    private MultiThreadedMessageDispatcher dispatcher;
    private Snmp snmp;
    private Address snmpTrapListenAddress;
    private ThreadPool threadPool;

    List<TrapEventHandler> trapEventHandlers;

    public TrapListener() {
        trapEventHandlers = new ArrayList<TrapEventHandler>();
    }

    /**
     * Initializes the SNMP trap receiver thread pool and starts listening for SNMP trap events
     *
     * @throws IOException when it fails to parse the destination address or to aquire a listening socket
     */
    public void run(String strListenAddress) {
        try {

            threadPool = ThreadPool.create("Trap Receiver Pool", 2);
            dispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());
            snmpTrapListenAddress = GenericAddress.parse(strListenAddress);
            TransportMapping<? extends Address> transport;
            if (snmpTrapListenAddress instanceof UdpAddress) {
                transport = new DefaultUdpTransportMapping((UdpAddress) snmpTrapListenAddress);
            } else {
                transport = new DefaultTcpTransportMapping((TcpAddress) snmpTrapListenAddress);
            }
            snmp = new Snmp(dispatcher, transport);
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv3());
            USM usm = new USM(SecurityProtocols.getInstance(),
                    new OctetString(MPv3.createLocalEngineID()), 0);
            SecurityModels.getInstance().addSecurityModel(usm);
            snmp.listen();

            snmp.addCommandResponder(this);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Delegates handling of received trap events to the registered handlers
     *
     * @param event
     */
    @Override
    public void processPdu(CommandResponderEvent event) {
        for (TrapEventHandler eventHandler : trapEventHandlers) {
            eventHandler.handleTrapEvent(event);
        }
    }

    public void addTrapEventHandler(TrapEventHandler handler) {
        trapEventHandlers.add(handler);
    }
}