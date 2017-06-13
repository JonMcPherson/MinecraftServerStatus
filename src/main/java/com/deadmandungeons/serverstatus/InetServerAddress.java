package com.deadmandungeons.serverstatus;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Hashtable;


/**
 *
 */
public class InetServerAddress extends MinecraftServer.Address {

    private final InetAddress inetAddress;

    private InetServerAddress(String host, int port) throws URISyntaxException, UnknownHostException {
        super(host, port);

        inetAddress = InetAddress.getByName(host);
    }

    private InetServerAddress(MinecraftServer.Address address) throws UnknownHostException {
        super(address);

        inetAddress = InetAddress.getByName(getHost());
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }


    public static InetServerAddress resolve(String host, int port) throws URISyntaxException, UnknownHostException {
        try {
            return lookupAddress(host);
        } catch (Exception e) {
            return new InetServerAddress(host, port);
        }
    }

    public static InetServerAddress resolve(MinecraftServer.Address address) throws UnknownHostException {
        if (address instanceof InetServerAddress) {
            return (InetServerAddress) address;
        }
        try {
            return lookupAddress(address.getHost());
        } catch (Exception e) {
            return new InetServerAddress(address);
        }
    }

    public static InetServerAddress resolve(String address) throws URISyntaxException, UnknownHostException {
        return resolve(new MinecraftServer.Address(address));
    }

    private static InetServerAddress lookupAddress(String host) throws Exception {
        // Lookup SRV records to find the real address
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = new InitialDirContext(env);
        Attributes attributes = context.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});

        String[] answer = attributes.get("srv").get().toString().split("\\s");
        String resolvedHost = answer[answer.length - 1];
        int port = Integer.parseInt(answer[answer.length - 2]);

        int dotIndex = resolvedHost.length() - 1;
        if (resolvedHost.charAt(dotIndex) == '.') {
            resolvedHost = resolvedHost.substring(0, dotIndex);
        }

        return new InetServerAddress(resolvedHost, port);
    }

}
