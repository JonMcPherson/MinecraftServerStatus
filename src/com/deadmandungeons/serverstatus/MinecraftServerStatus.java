package com.deadmandungeons.serverstatus;

import com.deadmandungeons.serverstatus.MinecraftServer.Address;
import com.deadmandungeons.serverstatus.ping.MinecraftPinger;
import com.deadmandungeons.serverstatus.ping.PingResponse;
import com.deadmandungeons.serverstatus.query.MinecraftQuery;
import com.deadmandungeons.serverstatus.query.QueryResponse;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Hashtable;

public class MinecraftServerStatus {

    private static final int DEFAULT_TIMEOUT = 6000;

    private MinecraftServerStatus() {
    }


    public static int ping(String host, int port) throws IOException, URISyntaxException {
        return doPing(resolveAddress(host, port));
    }

    public static int ping(String address) throws IOException, URISyntaxException {
        return doPing(resolveAddress(new Address(address)));
    }

    public static int ping(Address address) throws IOException {
        return doPing(resolveAddress(address));
    }

    private static int doPing(Address address) throws IOException {
        return MinecraftPinger.ping(address, DEFAULT_TIMEOUT);
    }


    public static MinecraftServer pingServer(String host, int port) throws IOException, URISyntaxException {
        return doPingServer(resolveAddress(host, port));
    }

    public static MinecraftServer pingServer(String address) throws IOException, URISyntaxException {
        return doPingServer(resolveAddress(new Address(address)));
    }

    public static MinecraftServer pingServer(Address address) throws IOException {
        return doPingServer(resolveAddress(address));
    }

    private static MinecraftServer doPingServer(Address address) throws IOException {
        return new MinecraftServer(MinecraftPinger.status(address, DEFAULT_TIMEOUT));
    }


    public static PingResponse pingServerStatus(String host, int port) throws IOException, URISyntaxException {
        return doPingServerStatus(resolveAddress(host, port));
    }

    public static PingResponse pingServerStatus(String address) throws IOException, URISyntaxException {
        return doPingServerStatus(resolveAddress(new Address(address)));
    }

    public static PingResponse pingServerStatus(Address address) throws IOException {
        return doPingServerStatus(resolveAddress(address));
    }

    private static PingResponse doPingServerStatus(Address address) throws IOException {
        return MinecraftPinger.statusAndPing(address, DEFAULT_TIMEOUT);
    }


    public static QueryResponse queryServerStatus(String host, int port) throws IOException, URISyntaxException {
        return doQueryServerStatus(resolveAddress(host, port));
    }

    public static QueryResponse queryServerStatus(String address) throws IOException, URISyntaxException {
        return doQueryServerStatus(resolveAddress(new Address(address)));
    }

    public static QueryResponse queryServerStatus(Address address) throws IOException {
        return doQueryServerStatus(resolveAddress(address));
    }

    private static QueryResponse doQueryServerStatus(Address address) throws IOException {
        return MinecraftQuery.queryServerStatus(address, DEFAULT_TIMEOUT);
    }


    private static Address resolveAddress(String host, int port) throws URISyntaxException {
        try {
            return lookupAddress(host);
        } catch (Exception e) {
            return new Address(host, port);
        }
    }

    private static Address resolveAddress(Address address) {
        try {
            return lookupAddress(address.getHost());
        } catch (Exception e) {
            return address;
        }
    }

    private static Address lookupAddress(String host) throws Exception {
        // Lookup SRV records to find the real address
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = new InitialDirContext(env);
        Attributes attributes = context.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});

        String[] answer = attributes.get("srv").get().toString().split("\\s");
        host = answer[answer.length - 1];
        int port = Integer.parseInt(answer[answer.length - 2]);

        int dotIndex = host.length() - 1;
        if (host.charAt(dotIndex) == '.') {
            host = host.substring(0, dotIndex);
        }

        return new Address(host, port);
    }

}
