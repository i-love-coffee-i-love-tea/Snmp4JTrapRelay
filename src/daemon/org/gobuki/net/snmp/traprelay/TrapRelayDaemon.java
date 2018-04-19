package org.gobuki.net.snmp.traprelay;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;


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
public class TrapRelayDaemon {

    // Listening for SNMP trap events on udp/162
    TrapListener trapListener;

    // Listening for tcp clients to register for receiving trap events
    ServerSocket trapRelayServer;

    // If no trap events and no client commands were received, sleep for this many ms
    int sleepTime = 500;

    public TrapRelayDaemon() {
        trapListener = new TrapListener();
    }

    /*
     * Listen for trap relay clients to connect. The server will push received traps to a client after it sent the
     * REGISTER command
     *
     * @param port
     */
    public void listenForClientConnections(String listeningAddress, int listeningPort) {

        // client connection loop, waits for new connections until a connection is opened
        // accepts new connections when the connections is closed
        waitForConnection: while (true) {
            System.out.println("waiting for a client connection");

            Socket clientSocket;
            PrintStream clientOutputStream;
            BufferedReader clientInputReader;

            try {
                // open new server listening socket
                trapRelayServer = new ServerSocket(listeningPort, 2, InetAddress.getByName(listeningAddress));

                clientSocket = trapRelayServer.accept();
                clientInputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                clientOutputStream = new PrintStream(clientSocket.getOutputStream());

                String clientCommand;
                System.out.println("SERVER: waiting for REGISTER command from client");
                waitForClientToRegister: while ((clientCommand = clientInputReader.readLine()) != null) {
                    System.out.println("Client: " + clientCommand);
                    if (clientCommand.indexOf("REGISTER") != -1) {
                        System.out.println("client " + clientSocket.getInetAddress() + " registered");
                        clientOutputStream.println("OK");
                        break waitForClientToRegister;
                    } else {
                        try {
                            TimeUnit.MILLISECONDS.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            System.err.println(Thread.currentThread().getName() + ": client connection closing");
                            e.printStackTrace();
                            break waitForClientToRegister; // TODO... leads to the waitForTrapOrCommand loop without registering, waitForClientToRegister should be in the waitForTrapOrCommand loop
                        }
                    }
                }

                // a trap json string, if the queue has one
                String jsonTrap;

                // client/server communication loop
                System.out.println("entering client communication loop for " + clientSocket.getInetAddress());
                waitForTrapOrCommand: while (true) {
                    try {
                        jsonTrap = trapListener.getConvertedTrapQueue().poll();

                        // Problem: if there are many traps coming in, they might cause
                        // client commands to be delayed until the spam stops
                        if (clientInputReader.ready()) {
                            // new client command
                            clientCommand = clientInputReader.readLine();

                            switch (clientCommand) {
                                case "QUIT":
                                    System.out.println("client " + clientSocket.getInetAddress() + " disconnected");
                                    clientOutputStream.close();
                                    clientSocket.close();
                                    trapRelayServer.close();

                                    break waitForTrapOrCommand;
                            }
                            System.out.println("client sent " + clientCommand);
                        } else if (jsonTrap != null) {
                            // new trap to send over to the client
                            //System.out.println("sending trap to client: " + jsonTrap);
                            clientOutputStream.println(jsonTrap);

                        } else {
                            System.out.println("sleeping");
                            TimeUnit.MILLISECONDS.sleep(sleepTime);
                        }
                    } catch (InterruptedException ex) {
                        System.err.println(Thread.currentThread().getName() + ": client connection closing");
                        ex.printStackTrace();
                        break waitForTrapOrCommand;
                    }
                }

            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    public static void main(String args[]) {

        if (args.length == 0) {
            // use defaults
            args = new String[]{ "localhost", "1162" };
        } else if (args.length != 2) {
            System.out.println("usage: " + TrapRelayDaemon.class.getCanonicalName() + " <listen address> <listen port>");
            System.exit(2);
        }

        // Start server thread for TCP clients to connect
        TrapRelayDaemon trapDaemon = new TrapRelayDaemon();
        // Start SNMP trap receiver threads
        trapDaemon.trapListener.setTrapEventConverter(new TrapEventJsonConverter());
        trapDaemon.trapListener.run("udp:0.0.0.0/162");
        trapDaemon.listenForClientConnections(args[0], Integer.parseInt(args[1]));
    }
}