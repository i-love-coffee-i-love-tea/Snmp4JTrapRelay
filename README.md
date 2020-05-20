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


## Manage certificates 

We will use the keytool to create SSL keys and for certificates for both server and clients.

The keys are wrapped in openssl compatible PKCS12 keystores.

For each client trust has to be setup by exporting and importing each others certificates into their peers keystore.

You can run the create-keystores.sh script to initialize example keystores and skip the next topics "Create the server key- and truststore" and
"Client key- and truststore setup" for testing.
``` 
cd scripts
./create-keystores.sh
```

### Server key- and truststore setup

**Create server keystore and keys**
```
keytool -genkey -alias sslserver -keystore sslserverkeys.p12 -storetype PKCS12 -storepass $PASS
```
When the keytool asks for your name "What is your first and last name?", you have to enter the hostname of the server.
You can press enter for all other questions, for testing purposes.


**Export server certificate**
```
keytool -export -alias sslserver -keystore sslserverkeys.p12 -file sslserver.cer -storetype PKCS12 -storepass $PASS
```


### Client key- and truststore setup

The alias is just the name under which the keys will be stored. You can choose it freely, it only has to be unique.
It is good practice to use the client name, something, you remeber or can look up and associate with this client.


**Create client keystore and keys**
```
keytool -genkey -alias sslclient -keystore sslclientkeys.p12 -storetype PKCS12 -storepass $PASS -keyalg RSA
```
When the keytool asks for your name "What is your first and last name?", you have to enter the hostname of the client.
You can press enter for all other questions, for testing purposes.

**Export client certificate**
```
keytool -export -alias sslclient -keystore sslclientkeys.p12 -file sslclient.cer -storetype PKCS12 -storepass $PASS
```

**Import client certificate into server truststore**
```
keytool -import -alias sslclient -keystore sslservertrust.p12 -file sslclient.cer -storetype PKCS12 -storepass $PASS
```
Answer "yes" to make the client trust the server certificate.

**Import server certificate into client truststore**
```
keytool -import -alias sslserver -keystore sslclienttrust.p12 -file sslserver.cer -storetype PKCS12 -storepass $PASS
```

## Run it

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



## TODO

   - Add MITM protection. See https://docs.oracle.com/javase/7/docs/api/javax/net/ssl/X509ExtendedTrustManager.html
   - DONE Add timestamps to converted traps
   - DONE add SSLSocket for TLS 1.2, create certificate store for server and trust store for client
        > https://stackoverflow.com/questions/28743482/java-sslserversocket-with-only-tls
   - DONE only allow connection with client ceritifcate
   - DONE allow simultaneous client connections
   - DONE use indidual trap queues for each client
   - extend REGISTER command to take an argument. So clients can register for SNMP trap events
     from sources they want to monitor.
   - add an UNREGISTER command
   - create a LIST channels command
   - DONE extend the protocol, so clients must ack received traps unless they want to receive it again 
   - DONE create client and server jars
   - DONE client: implement trap handler interface
   - DONE Add dead client detection
