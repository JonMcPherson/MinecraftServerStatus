package com.deadmandungeons.serverstatus;

import com.deadmandungeons.serverstatus.MinecraftServer.Address;
import com.deadmandungeons.serverstatus.ping.MinecraftPinger;
import com.deadmandungeons.serverstatus.ping.Pinger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(Parameterized.class)
public class PingTest {

    private static final int PING_TIMEOUT = 3000;
    private static final int TEST_SERVERS_PER_VERSION = 5;
    private static final String SERVER_LIST_URL_FORMAT = "http://minecraft-mp.com/version/%s/";

    private static ServerVersion currentVersion;

    private final InetServerAddress address;
    private final Pinger pinger;

    public PingTest(InetServerAddress address, ServerVersion version) {
        this.address = address;
        pinger = version.createPinger(address);

        if (version != currentVersion) {
            System.out.printf("\nTesting with servers on version: %s\n", version.base);
            currentVersion = version;
        }
    }


    @Test
    public void testPingServerStatus() throws IOException {
        try {
            System.out.println(pinger.pingServerStatus());
        } catch (ConnectException e) {
            System.out.println("Skipping server at " + address + " due to connection failure: " + e.getMessage());
        } catch (SocketTimeoutException e) {
            System.out.println("Skipping server at " + address + " due to socket timeout: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Failed to ping status for server at: " + address);
            throw e;
        }
    }

    @Parameters
    public static Collection<Object[]> findTestServers() {
        List<Object[]> testServers = new ArrayList<>();

        for (ServerVersion version : ServerVersion.values()) {
            System.out.printf("Searching for servers on version: %s  -  ", version.base);
            Set<Address> foundAddresses = new HashSet<>();
            for (String patch : version.patches) {
                String url = String.format(SERVER_LIST_URL_FORMAT, patch.replace('.', '_'));

                try {
                    Document doc = Jsoup.connect(url).followRedirects(false).get();

                    Elements addressElements = doc.select(".content table td:nth-child(2) span.badge-success ~ strong:last-child");

                    for (Element addressElem : addressElements) {
                        String addressRaw = addressElem.text().trim();
                        if (addressRaw.equalsIgnoreCase("private server")) {
                            continue;
                        }

                        try {
                            foundAddresses.add(new Address(addressRaw));
                        } catch (URISyntaxException e) {
                            // Ignore invalid addresses
                        }
                    }
                } catch (IOException e) {
                    System.err.printf("Failed to load the list of servers on version %s: %s\n", patch, e.getMessage());
                }
            }
            System.out.printf("Found %d/%d  -  ", foundAddresses.size(), TEST_SERVERS_PER_VERSION);

            int resolved = 0;
            if (!foundAddresses.isEmpty()) {
                List<Address> resolvedAddresses = new ArrayList<>(foundAddresses);
                Collections.shuffle(resolvedAddresses);

                for (int i = 0; i < resolvedAddresses.size() && resolved < TEST_SERVERS_PER_VERSION; i++) {
                    try {
                        InetServerAddress resolvedAddress = InetServerAddress.resolve(resolvedAddresses.get(i));

                        testServers.add(new Object[]{resolvedAddress, version});
                        resolved++;
                    } catch (UnknownHostException e) {
                        // Ignore unknown addresses
                        System.err.println(e.getMessage());
                    }
                }
            }
            System.out.printf("Resolved %d/%d\n", resolved, TEST_SERVERS_PER_VERSION);
        }
        return testServers;
    }


    private enum ServerVersion {
        V1_12(PingProtocol.CURRENT, "1.12", 0),
        V1_11(PingProtocol.CURRENT, "1.11", 0, 1, 2),
        V1_10(PingProtocol.CURRENT, "1.10", 0, 1, 2),
        V1_9(PingProtocol.CURRENT, "1.9", 0, 1, 2, 3, 4),
        V1_8(PingProtocol.CURRENT, "1.8", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        V1_7(PingProtocol.CURRENT, "1.7", 2, 4, 5, 6, 7, 8, 9, 10),
        V1_6(PingProtocol.LEGACY_47, "1.6", 1, 2, 3, 4),
        V1_5(PingProtocol.LEGACY_47, "1.5", 1, 2),
        V1_4(PingProtocol.LEGACY_47, "1.4", 2, 4, 5, 6, 7),
        V1_3(PingProtocol.LEGACY_17, "1.3", 1, 2),
        V1_2(PingProtocol.LEGACY_17, "1.2", 1, 2, 3, 4, 5),
        V1_1(PingProtocol.LEGACY_17, "1.1", 0),
        V1_0(PingProtocol.LEGACY_17, "1.0", 0, 1);

        private final PingProtocol protocol;
        private final String base;
        private final List<String> patches;

        ServerVersion(PingProtocol protocol, String base, int... patches) {
            this.protocol = protocol;
            this.base = base;

            List<String> versionPatches = new ArrayList<>();
            versionPatches.add(base);
            for (int patch : patches) {
                versionPatches.add(base + "." + patch);
            }
            this.patches = Collections.unmodifiableList(versionPatches);
        }

        private Pinger createPinger(InetServerAddress address) {
            MinecraftPinger pinger = new MinecraftPinger(address, PING_TIMEOUT);
            if (protocol == PingProtocol.LEGACY_47) {
                return pinger.legacy47();
            } else if (protocol == PingProtocol.LEGACY_17) {
                return pinger.legacy17();
            }
            return pinger;
        }

    }

    private enum PingProtocol {
        CURRENT,
        LEGACY_47,
        LEGACY_17,
    }

}
