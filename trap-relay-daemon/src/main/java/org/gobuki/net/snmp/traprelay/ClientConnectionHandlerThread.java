package org.gobuki.net.snmp.traprelay;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 */
public class ClientConnectionHandlerThread extends Thread {

    // private client trap queue
    Queue<String> trapQueue;

    // Listening for SNMP trap events on udp/162
    TrapListener trapListener;

    // client socket
    Socket clientSocket;
    PrintWriter out;
    BufferedReader in;

    AtomicInteger clientCount;

    enum ClientCommand {
        QUIT;
    }

    // If no trap events and no client commands were received, sleep for this many ms
    int sleepTime = 500;
    int maximumAckWaitTime = 5000;

    public ClientConnectionHandlerThread(Socket socket, TrapListener trapListener, AtomicInteger clientCount) {
        this.clientSocket = socket;
        this.trapListener = trapListener; // make sure it is thread safe
        this.clientCount = clientCount;
        this.trapQueue = new ConcurrentLinkedQueue<String>();
    }

    /*
     * Handle a client connection
     *
     * Waits for a REGISTER command and then for other client commands or traps
     *
     * @param port
     */
    @Override
    public void run() {

        System.out.println("Handling client " + getClientName());

        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String clientCommand;
            System.out.println("SERVER: waiting for REGISTER command from client");
            waitForClientToRegister: while ((clientCommand = in.readLine()) != null) {
                log("Client send command '" + clientCommand + "'");
                if (clientCommand.indexOf("REGISTER") == 0) {
                    log("registered");
                    out.println("OK");
                    break waitForClientToRegister;
                } else {
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        log("Client connection closing");
                        e.printStackTrace();
                        break waitForClientToRegister; // TODO... leads to the waitForTrapOrCommand loop without registering, waitForClientToRegister should be in the waitForTrapOrCommand loop
                    }
                }
            }

            // a trap json string, if the queue has one
            String strTrapInfo;

            // client/server communication loop
            log("Sending incoming traps to client");
            int sleepCount = 0;
            waitForTrapOrCommand: while (true) {
                try {
                    strTrapInfo = trapQueue.poll();

                    // Problem: if there are many traps coming in, they might cause
                    // client commands to be delayed until the spam stops
                    if (in.ready()) {
                        // new client command
                        clientCommand = in.readLine();

                        if (ClientCommand.QUIT.equals(clientCommand)) {
                            log("disconnected");
                            out.close();
                            clientSocket.close();
                            break waitForTrapOrCommand;
                        }
                        log("received command '" + clientCommand + "'");
                        //sleepCount = 0;
                    } else if (strTrapInfo != null) {
                        // new trap to send over to the client
                        log("Sending trap to client");

                        out.println(strTrapInfo);

                        // wait for ack
                        long tTrapSent = System.currentTimeMillis();
                        boolean ackReceived = false;
                        waitForAck: while (!ackReceived && (System.currentTimeMillis() - tTrapSent) < maximumAckWaitTime) {
                            log("Waiting for ACK");
                            String response;
                            if ((response = in.readLine()) != null) {
                                if (response.equals("ACK")) {
                                    ackReceived = true;
                                    log("ACK received");
                                    break waitForAck;
                                }
                            }
                            TimeUnit.MILLISECONDS.sleep(50);
                        }
                        if (!ackReceived) {
                            // client seems to be dead
                            break waitForTrapOrCommand;
                        }
                    } else {
                        TimeUnit.MILLISECONDS.sleep(50);
                    }
                } catch (InterruptedException ex) {
                    log("connection closed");
                    ex.printStackTrace();
                    break waitForTrapOrCommand;
                }
            }
        } catch (IOException e) {
            System.err.println(e);
        }

        System.out.println("Connection closed; client count: " + clientCount.decrementAndGet());
    }

    public void log(String message) {
        System.out.println(getClientName() + ": " + message);
    }

    public void offerTrap(String strTrapInfo) {
        trapQueue.offer(strTrapInfo);
    }

    public String getClientName() {
        return clientSocket.getInetAddress() + ":" + clientSocket.getPort();
    }
}