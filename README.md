# Snmp4JTrapRelay

Proof of concept of a small daemon that collects SNMP trap events and puts them in a queue
for tcp clients to connect, register and receive SNMP trap events in json format

## Goals 
 - Small daemon, externalizing the SNMP-trap-listening-part from a larger application, so the application can run with normal user permission
   (Background: on linux a process must run with root permission to open sockets on ports below 1024)
 - Decouple development lifecycles of parts with different responsibilities (ui, trap listening), so the main application doesn't have to run to collect traps.
 - Be able to develop the larger management application independently, without having to run the ide as root for debugging


## How does it work?

```
 .------------------------.
 |    TrapRelayClient     |   <------- converts received traps using the TrapEventConverter interface
 *------------------------*
             |
         tcp/1162
             |
 .---------------------------------------------------------------------------- - - -
 | SnmpTrapRelay running as a daemon with root privileges
 *---------------------------------------------------------------------------- - - -
             |                                    |
    udp/162 SNMP trap port               udp/162 SNMP trap port
             |                                    |
             |                                    |
 .-----------------------------.      .-----------------------------.     .--- - - -
 | SNMP agent generating traps |      | another agent sending traps |     |
 *-----------------------------*      *-----------------------------*     *--- - - -
```

## Compiling

$ mvn package


## Running it

1. Start the trap relay daemon:

    ```
    $ cd trap-relay-daemon/target  
    $ sudo java -jar trap-relay-daemon/target/trap-relay-daemon-1.0-SNAPSHOT-jar-with-dependencies.jar
    ```
    
2. Start the client. It will connect to the daemon and print SNMP traps to the console in JSON format:

    ```
    $ java -jar trap-relay-client/target/trap-relay-client-1.0-SNAPSHOT-jar-with-dependencies.jar
    ```
    
3. Send a test trap:

    ```    
    $ snmptrap -v 2c -c public localhost '' 1.3.6.1.4.1.8072.2.3.0.1 1.3.6.1.4.1.8072.2.3.2.1 i 123456
    ```

The client output should look like this:

```
Server: OK
registered
Server sent a trap: { "trapSrc": "127.0.0.1/52401", "secLevel": "1", "secModel": "2", "secName": "public", 
                      "variables": { "1.3.6.1.2.1.1.3.0": "7 days, 14:24:34.17",
                                     "1.3.6.1.6.3.1.1.4.1.0": "1.3.6.1.4.1.8072.2.3.0.1",
                                     "1.3.6.1.4.1.8072.2.3.2.1": "123456" } }
```

If the programs are run without arguments the server listens for client connections on localhost port 1162 by default
and the client by default connects to the same socket.

They both take a hostname or ip as first parameter and a port number as second parameter to change this behaviour. 




## Next steps to make it a mature project

   - add timestamps to converted traps
   - add SSLSocket for TLS 1.2, create certificate store for server and trust store for client
        > https://stackoverflow.com/questions/28743482/java-sslserversocket-with-only-tls
   - only allow connection with client ceritifcate
   - allow simultaneous client connections
   - create indidual queues for each snmp agent sending traps - limit maximum ammount
   - extend REGISTER command to take an argument. So clients can register for SNMP trap events
     from sources they want to monitor.
   - add an UNREGISTER command
   - create a LIST channels command
   - extend the protocol, so clients must ack received traps unless they want to receive it again
   - create client and server jars
   - client: implement exchangeable trap handling behaviour


I have not used redis yet, but to me parts of this increasingly look similar.
Perhaps i might as well use this, but I enjoy learning about server programming and like to find my own solutions.
Also I have a feeling redis might pull in too many dependencies, which i don't need and want.
It certainly has its many use cases where it does a great job, but it also has a ton of
features like clustering which are overkill for my usecase
  
Most ugly problem apart from missing features: When a client disconnects without
sending register first, the server will hang in an infinite loop waiting for queue events.
