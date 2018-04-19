package org.gobuki.net.snmp.traprelay;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;


/**
 * Test traps can be generated using net-snmp:
 * snmptrap -v 2c -c public localhost '' 1.3.6.1.4.1.8072.2.3.0.1 1.3.6.1.4.1.8072.2.3.2.1 i 123456
 * 
 */
public class TrapRelayClient {

    Socket socket = null;
    PrintStream ps = null;
    InputStreamReader ir = null;
    BufferedReader  bir = null;

    public static void main(String[] args) {

        if (args.length == 0) {
            args = new String[]{"localhost", "1162"};
        }

        TrapRelayClient client = new TrapRelayClient();
        client.connectToServer(args[0], Integer.parseInt(args[1]));
    }

    public TrapRelayClient() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (ps != null) {
                ps.println("QUIT");
                ps.close();

            }
            System.out.println("shutdown hook called");
        }));
    }

    public void connectToServer(String serverAddress, int serverPort) {
        try {
            socket = new Socket(serverAddress, serverPort);
            ps = new PrintStream(socket.getOutputStream());
            ir = new InputStreamReader(socket.getInputStream());
            bir = new BufferedReader(ir);
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + serverAddress);
        } catch (IOException e) {
            System.err.println("Failed connecting with " + serverAddress + ". Is the relay service running?");
        }

        if (socket != null && bir != null && ps != null) {
            try {

                ps.println("REGISTER all");

                String responseLine;
                waitForResigsterResponse: while ((responseLine = bir.readLine()) != null) {
                    System.out.println("Server: " + responseLine);
                    if (responseLine.indexOf("OK") != -1) {
                        System.out.println("registered");
                        break waitForResigsterResponse;
                    }
                }

                // event loop
                receiveTraps: while ((responseLine = bir.readLine()) != null) {
                    System.out.println("Server sent a trap: " + responseLine);

                    // handle recevied traps here. possibly using jackson or javax JSON objects

                    // how to interrupt?
                    // by shutdown hook (implemented)
                    // or server shutdown/restart command (idea)
                    // - clients could go into a polling mode, trying to reconnect
                }

                // close everything
                ps.close();
                bir.close();
                ir.close();
                socket.close();
            } catch (UnknownHostException e) {
                System.err.println("Unknown host: " + e);
            } catch (IOException e) {
                System.err.println("I/O Error:  " + e);
            }
        }
    }
}