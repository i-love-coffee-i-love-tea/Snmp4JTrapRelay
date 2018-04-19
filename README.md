# Snmp4JTrapRelay
Proof of concept of a small daemon that collects SNMP trap events and puts them in a queue
for tcp clients to connect, register and receive SNMP trap events in json format

Goals:
 - Small daemon, externalizing the SNMP-trap-listening-part from a larger application, so the application can run with normal user permission
   (Background: on linux a process must run with root permission to open sockets on ports below 1024)
 - Decouple development lifecycles of parts with different responsibilities (ui, trap listening), so the main application doesn't have to run to collect traps


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


Next steps to make it a mature project:

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


I have not used redis yet, but to me it increasingly looks like it.
Perhaps i might as well use this, but I enjoy learning about server programming and like to find my own solutions.
Also I have a feeling redis might pull in too many dependencies, which i don't need and want.
It certainly has its many use cases where it does a great job, but it also has a ton of
features like clustering which are overkill for my usecase
  
Most ugly problem apart from missing features: When a client disconnects without
sending register first, the server will hang in an infinite loop waiting for queue events.
