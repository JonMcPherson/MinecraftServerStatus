# MinecraftServerStatus

A utility for querying Minecraft servers for the information they expose through the [Server List Ping](http://wiki.vg/Server_List_Ping) protocol and the optional [Query](http://wiki.vg/Query) protocol.

This is essentially a java implementation of Dinnerbone's [mcstatus](https://github.com/Dinnerbone/mcstatus) but with greatly improved range of support and features.  
Practically every server version is supported from `Beta-1.8` to the latest `1.12` including servers with nonstandard netcode that breaks the protocol.  This utility can query more servers than the Minecraft client can itself.

---

### Server List Ping (SLP) Protocol

Supported by every server from version `Beta-1.8` and above.

**Exposed Information:**
- Description
- Favicon (1.7.x and above)
- Version (1.4.x and above)
  - Name
  - Protocol #
- Players
  - Max Count
  - Online Count
  - Sample Online List (1.7.x and above)
    - Username
    - UUID

Find more protocol details on the [SLP wiki page](http://wiki.vg/Server_List_Ping).

#### Usage

For simplicity, you can just call a single method in `MinecraftServerStatus`.  
This uses the latest protocol which is supported by all Minecraft servers on version 1.7 or above.
```java
String address = "mc.deadmandungeons.com";
PingResponse serverStatus = MinecraftServerStatus.pingServerStatus(address);
System.out.println(serverStatus);
```

For more control and support for older server versions, you can use `MinecraftPinger` directly and select which protocol to use.

```java
int timeout = 3000;
InetServerAddress address = InetServerAddress.resolve("mc.deadmandungeons.com");
MinecraftPinger pinger = new MinecraftPinger(address, timeout);
 
// Latest protocol for server versions 1.7.x and above
PingResponse serverStatus = pinger.pingServerStatus();
System.out.println(serverStatus);
 
// Use legacy47 for server versions 1.4.x to 1.6.x (or above)
PingResponse serverStatus47 = pinger.legacy47().pingServerStatus();
System.out.println(serverStatus47);
 
// Use legacy17 for server versions Beta-1.8 to 1.3.x (or above)
PingResponse serverStatus17 = pinger.legacy17().pingServerStatus();
System.out.println(serverStatus17);
```

Additionally, you can use the `pingServer()` and `ping()` methods to retrieve less information and improve efficiency when the full server status is not required.
```java
MinecraftServer server = pinger.pingServer();
System.out.println(server);
 
int latency = pinger.ping();
System.out.println(latency);
```

---

### Query Protocol
Supported by every server from version `Beta-1.9` and above.  
Most servers have this **disabled by default** and it must be explicitly enabled in its `server.properties` file.

**Exposed Information:**
- Description
- Version
  - name
- Players
  - max count
  - online count
  - full online list
    - username
- Software
  - server type
  - plugin list

Find more protocol details on the [Query wiki page](http://wiki.vg/Query)

#### Usage

```java
String address = "mc.deadmandungeons.com";
QueryResponse serverStatus = MinecraftServerStatus.queryServerStatus(address);
System.out.println(serverStatus);
```

---

### Compiling

Using Maven, simply add the following repository and dependency to your project POM definition:
```xml
<repository>
	<id>deadman-dungeons</id>
	<url>http://deadmandungeons.com/artifactory/public</url>
</repository>
```
```xml
<dependency>
    <groupId>com.deadmandungeons</groupId>
    <artifactId>mc-server-status</artifactId>
    <version>1.2.0</version>
</dependency>
```
