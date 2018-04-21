package org.gobuki.net.snmp.traprelay;

import org.snmp4j.CommandResponderEvent;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * Proof of concept for a small daemon that collects SNMP trap events and puts them in a queue
 * for tcp clients to connect, register and receive SNMP trap events in json format
 *
 * Goals:
 *  - Small daemon, externalizing the SNMP-trap-listening-part from a larger application, so the application can run with normal user permission
 *    (Background: on linux a process must run with root permission to open sockets on ports below 1024)
 *  - Decouple development life responsibilities (ui, trap listening), so the main aplication doesn't have to run to collect traps
 *

 *
 *  .------------------------.
 *  |    TrapRelayClient     |   <------- converts received traps using the TrapEventConverter interface
 *  *------------------------*
 *              |
 *          tcp/1162
 *              |
 *  .---------------------------------------------------------------------------- - - -
 *  | SnmpTrapRelay running as a daemon with root privileges
 *  *---------------------------------------------------------------------------- - - -
 *              |                                    |
 *     udp/162 SNMP trap port               udp/162 SNMP trap port
 *              |                                    |
 *              |                                    |
 *  .-----------------------------.      .-----------------------------.     .--- - - -
 *  | SNMP agent generating traps |      | another agent sending traps |     |
 *  *-----------------------------*      *-----------------------------*     *--- - - -
 *
 *
 * Next steps to make it a mature project:
 *
 *
 *    - add timestamps to converted traps
 *    - add SSLSocket for TLS 1.2, create certificate store for server and trust store for client
 *         > https://stackoverflow.com/questions/28743482/java-sslserversocket-with-only-tls
 *    - only allow connection with client ceritifcate
 *    - allow simultaneous client connections
 *    - create indidual queues for each snmp agent sending traps - limit maximum ammount
 *    - extend REGISTER command to take an argument. So clients can register for SNMP trap events
 *      from sources they want to monitor.
 *    - add an UNREGISTER command
 *    - create a LIST channels command
 *    - extend the protocol, so clients must ack received traps unless they want to receive it again
 *    - create client and server jars
 *    - client: implement exchangeable trap handling behaviour
 *
 *    I have not used redis yet, but to me it increasingly looks like it.
 *    Perhaps i might as well use this, but I enjoy learning about server programming and like to find my own solutions.
 *    Also I have a feeling redis might pull in too many dependencies, which i don't need and want.
 *    It certainly has its many use cases where it does a great job, but it also has a ton of features like clustering.
 *    Overkill for my use case.
 *
 *    Most ugly problem apart from missing features: When a client disconnects without
 *    sending register first, the server will hang in an infinite loop waiting for queue events.
 *
 */
public class TrapRelayDaemon implements TrapEventHandler {

    public static final String REQUIRED_ENCRYPTION_PROTOCOL = "TLSv1.2";

    // Listening for SNMP trap events on udp/162
    TrapListener trapListener;

    TrapEventConverter<String> trapEventConverter;

    SSLServerSocket sslServerSocket;

    AtomicInteger clientCount;

    // If no trap events and no client commands were received, sleep for this many ms
    int sleepTime = 50;

    Set<ClientConnectionHandlerThread> clientConnectionHandlerThreads;

    public TrapRelayDaemon() {
        trapListener = new TrapListener();
        clientCount = new AtomicInteger(0);
        clientConnectionHandlerThreads = new HashSet<ClientConnectionHandlerThread>();
    }

    /*
     * Listen for trap relay clients to connect. The server will push received traps to a client after it sent the
     * REGISTER command
     *
     * @param port
     */
    public void listenForClientConnections(String listeningAddress, int listeningPort) {

        SSLServerSocketFactory sslServerSocketFactory =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

        System.out.println("Supported cipher suites:");
        for (String suite : sslServerSocketFactory.getSupportedCipherSuites()) {
            System.out.println("\t" + suite);
        }

        try {
            // open new server listening socket
            sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(listeningPort, 2, InetAddress.getByName(listeningAddress));
            sslServerSocket.setNeedClientAuth(true);
            requireProtocol(REQUIRED_ENCRYPTION_PROTOCOL);
        } catch (ProtocolException e) {
            System.err.println(e.getMessage());
            System.err.println("exiting");
            System.exit(2);
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
            System.err.println("exiting");
            System.exit(2);
        }

        // client connection loop, waits for new connections until a connection is opened
        // accepts new connections when the connections is closed
        System.out.println("Waiting for client connections");
        acceptClientConnections: while (true) {
            try {
                SSLSocket sslClientSocket = (SSLSocket) sslServerSocket.accept();

                ClientConnectionHandlerThread t = new ClientConnectionHandlerThread(sslClientSocket, trapListener, clientCount);
                clientConnectionHandlerThreads.add(t);
                t.start();

                System.out.println("connection opened; client count:" + clientCount.incrementAndGet());
                TimeUnit.MILLISECONDS.sleep(sleepTime);
            } catch (IOException e) {
                System.out.println(e);
                break acceptClientConnections;
            } catch (InterruptedException e) {
                System.out.println("interrupted");
                break acceptClientConnections;
            }
        }
    }

    /**
     * Checks if a protocol is supported. If yes, it enables only this protocol for the sslServerSocket,
     * throws an exception otherwise.
     *
     * @param protocol
     * @throws ProtocolException if the protocol isn't found in the list of supported protocols and thus can not be enabled
     */
    private void requireProtocol(String protocol) throws ProtocolException {
        System.out.println("Server supports these encryption protocols:");
        boolean protocolIsSupported = false;
        for (String supportedProtocol : sslServerSocket.getSupportedProtocols()) {
            System.out.println("\t" + supportedProtocol);
            if (supportedProtocol.equalsIgnoreCase(protocol)) {
                protocolIsSupported = true;
                break;
            }
        }
        if (protocolIsSupported) {
            sslServerSocket.setEnabledProtocols(new String[]{protocol});
            System.out.println("Set '" + protocol + "' as only supported protocol.");
        } else {
            throw new ProtocolException("Required protocol not supported by server: " + protocol);
        }
    }

    public void setTrapEventConverter(TrapEventConverter converter) {
        this.trapEventConverter = converter;
    }

    /**
     * Is called when the TrapListener recevied a trap.
     * Distributes the trap event to all clients.
     *
     * @param event
     */
    @Override
    public void handleTrapEvent(CommandResponderEvent event) {

        for (ClientConnectionHandlerThread clientThread : clientConnectionHandlerThreads) {
            if (clientThread.isAlive()) {
                clientThread.offerTrap(trapEventConverter.convertTrap(event));
            } else {
                clientConnectionHandlerThreads.remove(clientThread);
            }
            // TODO: else remove?!
        }
    }

    public static void main(String args[]) {

        //System.setProperty("javax.net.debug", "all");
        System.setProperty("javax.net.ssl.keyStore", "sslserverkeys.p12");
        System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");
        System.setProperty("javax.net.ssl.keyStorePassword", "password");
        System.setProperty("javax.net.ssl.trustStore", "sslservertrust.p12");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");

        if (args.length == 0) {
            // use defaults
            args = new String[]{ "localhost", "1162" };
        } else if (args.length == 2) {
            // ok
        } else {
            System.out.println("Usage: " + TrapRelayDaemon.class.getCanonicalName() + " <listen address> <listen port>");
            System.exit(2);
        }

        TrapRelayDaemon trapDaemon = new TrapRelayDaemon();
        trapDaemon.setTrapEventConverter(new TrapEventJsonConverter());
        trapDaemon.trapListener.addTrapEventHandler(trapDaemon);
        // Start SNMP trap receiver threads

        trapDaemon.trapListener.run("udp:0.0.0.0/162");

        // Start server thread for TCP clients to connect
        trapDaemon.listenForClientConnections(args[0], Integer.parseInt(args[1]));
    }

}