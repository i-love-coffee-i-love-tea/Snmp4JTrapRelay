package org.gobuki.net.snmp.traprelay;

import org.gobuki.net.snmp.traprelay.handler.JsonObjectTrapHandler;
import org.gobuki.net.snmp.traprelay.handler.SimpleLoggingTrapHandler;
import org.gobuki.net.snmp.traprelay.handler.TrapHandler;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Test traps can be generated using net-snmp:
 * snmptrap -v 2c -c public localhost '' 1.3.6.1.4.1.8072.2.3.0.1 1.3.6.1.4.1.8072.2.3.2.1 i 123456
 * 
 */
public class TrapRelayClient {

    Socket socket;
    BufferedReader in;
    PrintStream out;

    List<TrapHandler> trapHandlers;

    public static void main(String[] args) {

        System.setProperty("javax.net.ssl.keyStore", "sslclientkeys.p12");
        System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");
        System.setProperty("javax.net.ssl.keyStorePassword", "password");
        System.setProperty("javax.net.ssl.trustStore", "sslclienttrust.p12");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");

        if (args.length == 0) {
            args = new String[]{"localhost", "1162"};
        }

        TrapRelayClient client = new TrapRelayClient();
        client.addTrapHandler(new SimpleLoggingTrapHandler());
        client.addTrapHandler(new JsonObjectTrapHandler());
        client.connectToServer(args[0], Integer.parseInt(args[1]));
    }

    public TrapRelayClient() {
        // Handle CTRL+c
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (out != null) { // if out isnt null socket must also be != null
                    try {
                        out.println("QUIT");
                        socket.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }));
        trapHandlers = new ArrayList<TrapHandler>();
    }

    public void connectToServer(String serverAddress, int serverPort) {
        SSLSocketFactory sslSocketFactory =
                (SSLSocketFactory) SSLSocketFactory.getDefault();

        try {
            socket = sslSocketFactory.createSocket(serverAddress, serverPort);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintStream(socket.getOutputStream());

        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + serverAddress);
        } catch (IOException e) {
            System.err.println("Failed connecting with " + serverAddress + ". Is the relay service running?");
        }

        if (socket != null && in != null && out != null) {
            try {
                out.println("REGISTER all");

                String responseLine;
                waitForResigsterResponse: while ((responseLine = in.readLine()) != null) {
                    System.out.println("Server: " + responseLine);
                    if (responseLine.indexOf("OK") != -1) {
                        System.out.println("registered");
                        break waitForResigsterResponse;
                    }
                }

                // event loop
                receiveTraps: while ((responseLine = in.readLine()) != null) {
                    for (TrapHandler handler : trapHandlers) {
                        handler.handleTrap(responseLine);
                    }
                    //System.out.println("Sending ACK");
                    out.println("ACK");
                }

                // close everything
                out.close();
                in.close();
                socket.close();
            } catch (UnknownHostException e) {
                System.err.println("Unknown host: " + e);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public void addTrapHandler(TrapHandler trapHandler) {
        this.trapHandlers.add(trapHandler);
    }
}