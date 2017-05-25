package com.deadmandungeons.serverstatus.query;

import com.deadmandungeons.serverstatus.ByteUtils;
import com.deadmandungeons.serverstatus.MinecraftServer.Address;
import com.deadmandungeons.serverstatus.MinecraftServer.Description;
import com.deadmandungeons.serverstatus.MinecraftServer.Version;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinecraftQuery {

    private static final byte[] UDP_MAGIC = {(byte) 0xFE, (byte) 0xFD};
    private static final byte HANDSHAKE_REQUEST_TYPE = 9;
    private static final byte STAT_REQUEST_TYPE = 0;
    private static final int SESSION_ID = 1;

    public static QueryResponse queryServerStatus(Address address, int timeout) throws IOException {
        InetAddress inetAddress = InetAddress.getByName(address.getHost());

        byte[] handshakeRequest = createRequest(HANDSHAKE_REQUEST_TYPE, SESSION_ID, new byte[0]);

        // should be 11 bytes total
        int val = 11 - handshakeRequest.length;

        try (DatagramSocket socket = createSocket()) {
            handshakeRequest = ByteUtils.padArrayEnd(handshakeRequest, val);

            DatagramPacket handshakePacket = new DatagramPacket(handshakeRequest, handshakeRequest.length, inetAddress, address.getPort());
            byte[] handshakeResponse = sendUDP(socket, handshakePacket, timeout);

            int token = Integer.parseInt(new String(handshakeResponse, StandardCharsets.UTF_8).trim());

            byte[] payload = ByteUtils.padArrayEnd(ByteUtils.intToBytes(token), 4);
            byte[] queryRequest = createRequest(STAT_REQUEST_TYPE, SESSION_ID, payload);

            DatagramPacket queryPacket = new DatagramPacket(queryRequest, queryRequest.length, inetAddress, address.getPort());
            byte[] queryResponse = sendUDP(socket, queryPacket, timeout);

            try {
                return parseQueryResponse(queryResponse);
            } catch (URISyntaxException e) {
                throw new IOException("Server responded with an invalid IP address", e);
            }
        }
    }


    private static byte[] sendUDP(DatagramSocket socket, DatagramPacket requestPacket, int timeout) throws IOException {
        socket.send(requestPacket);

        // receive a response in a new packet
        byte[] out = new byte[9999];
        DatagramPacket responsePacket = new DatagramPacket(out, out.length);
        socket.setSoTimeout(timeout);
        socket.receive(responsePacket);

        return responsePacket.getData();
    }

    private static DatagramSocket createSocket() throws IOException {
        DatagramSocket socket = null;
        int localPort = 25565;
        while (socket == null && localPort < Short.MAX_VALUE * 2) {
            try {
                // create the socket
                socket = new DatagramSocket(localPort);
            } catch (BindException e) {
                // increment if port is already in use
                ++localPort;
            }
        }
        return socket;
    }

    private static byte[] createRequest(byte requestType, int sessionId, byte[] payload) throws IOException {
        int size = 1460;
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(size);
        DataOutputStream dataStream = new DataOutputStream(byteStream);

        dataStream.write(UDP_MAGIC);
        dataStream.write(requestType);
        dataStream.writeInt(sessionId);
        dataStream.write(payload);

        return byteStream.toByteArray();
    }

    private static QueryResponse parseQueryResponse(byte[] response) throws IOException, URISyntaxException {
        response = ByteUtils.trim(response);
        byte[][] data = ByteUtils.split(response);

        String descriptionText = new String(data[3]);
        Description description = new Description(descriptionText, null);
        // String gameMode = new String(data[5]); // Hardcoded to SMP
        // String gameId = new String(data[7]); // Hardcoded to MINECRAFT
        String versionName = new String(data[9]);
        Version version = new Version(versionName, 0); // protocol is not returned in response

        String mapName = new String(data[13]);
        int playerCount = Integer.parseInt(new String(data[15]));
        int playerMax = Integer.parseInt(new String(data[17]));

        int addressPort = Integer.parseInt(new String(data[19]));
        String addressIp = new String(data[21]);
        Address address = new Address(addressIp, addressPort);

        String pluginsStr = new String(data[11]);
        int index = pluginsStr.indexOf(": ");
        String serverType = (index != -1 ? pluginsStr.substring(0, index) : "");
        List<String> plugins = Arrays.asList((!serverType.isEmpty() ? pluginsStr.replace(serverType + ": ", "") : pluginsStr).split("; "));

        List<String> playerList = new ArrayList<>(data.length - 24);
        for (int i = 25; i < data.length; i++) {
            playerList.add(new String(data[i]));
        }
        QueryResponse.PlayersList players = new QueryResponse.PlayersList(playerMax, playerCount, playerList);

        String favicon = null; // TODO favicon is needed for ServerInfo, but is not returned in query response...

        return new QueryResponse(address, players, version, description, favicon, mapName, serverType, plugins);
    }

}
