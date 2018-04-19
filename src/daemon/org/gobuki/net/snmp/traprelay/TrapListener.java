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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TrapListener implements CommandResponder {

    private MultiThreadedMessageDispatcher dispatcher;
    private Snmp snmp;
    private Address snmpTrapListenAddress;
    private ThreadPool threadPool;

    Queue<String> convertedTrapQueue;
    TrapEventConverter<String> trapEventConverter;

    public TrapListener() {
        convertedTrapQueue = new ConcurrentLinkedQueue<String>();
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
     * Converts received SNMP traps using a TrapEventConverter implementation and
     * queues it
     *
     * @param event
     */
    @Override
    public void processPdu(CommandResponderEvent event) {
        if (getConvertedTrapQueue().offer(trapEventConverter.convertTrap(event))) {
            System.out.println("Trap relayed");
        } else {
            System.err.println("Dropping trap event. Queue is full.");
        }
    }

    public Queue<String> getConvertedTrapQueue() {
        return convertedTrapQueue;
    }

    public void setTrapEventConverter(TrapEventConverter converter) {
        this.trapEventConverter = converter;
    }
}