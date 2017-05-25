package com.deadmandungeons.serverstatus.ping;

import com.deadmandungeons.serverstatus.ByteUtils;
import com.deadmandungeons.serverstatus.MinecraftServer.Address;
import com.deadmandungeons.serverstatus.MinecraftServer.Description;
import com.deadmandungeons.serverstatus.MinecraftServer.Version;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MinecraftPinger implements AutoCloseable {

    private static final byte HANDSHAKE_PACKET = 0x00;
    private static final byte STATUS_REQUEST_PACKET = 0x00;
    private static final byte PING_REQUEST_PACKET = 0x01;
    private static final byte PROTOCOL_VERSION = 4;
    private static final byte HANDSHAKE_STATE = 1;
    private static final long PING_TOKEN = 0;

    private final Address address;
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    private MinecraftPinger(Address address, InetAddress inetAddress, int timeout) throws IOException {
        this.address = address;

        socket = new Socket();
        socket.connect(new InetSocketAddress(inetAddress, address.getPort()), timeout);

        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    public static int ping(Address address, int timeout) throws IOException {
        InetAddress inetAddress = InetAddress.getByName(address.getHost());
        try (MinecraftPinger pinger = new MinecraftPinger(address, inetAddress, timeout)) {
            return pinger.handshake().ping();
        }
    }

    public static PingResponse status(Address address, int timeout) throws IOException {
        InetAddress inetAddress = InetAddress.getByName(address.getHost());
        try (MinecraftPinger pinger = new MinecraftPinger(address, inetAddress, timeout)) {
            return pinger.handshake().status(false);
        }
    }

    public static PingResponse statusAndPing(Address address, int timeout) throws IOException {
        InetAddress inetAddress = InetAddress.getByName(address.getHost());
        try (MinecraftPinger pinger = new MinecraftPinger(address, inetAddress, timeout)) {
            return pinger.handshake().status(true);
        }
    }


    private MinecraftPinger handshake() throws IOException {
        ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
        DataOutputStream handshake = new DataOutputStream(handshakeBytes);

        handshake.writeByte(HANDSHAKE_PACKET);
        ByteUtils.writeVarInt(handshake, PROTOCOL_VERSION);
        ByteUtils.writeVarInt(handshake, address.getHost().length());
        handshake.writeBytes(address.getHost());
        handshake.writeShort(address.getPort());
        ByteUtils.writeVarInt(handshake, HANDSHAKE_STATE);

        ByteUtils.writeVarInt(output, handshakeBytes.size());
        output.write(handshakeBytes.toByteArray());

        return this;
    }

    private int ping() throws IOException {
        long timeSent = System.nanoTime();
        output.writeByte(0x09); // Size of packet
        output.writeByte(PING_REQUEST_PACKET);
        output.writeLong(PING_TOKEN);

        ByteUtils.readVarInt(input); // Read and ignore packet size
        long timeReceived = System.nanoTime();
        int id = ByteUtils.readVarInt(input);
        if (id != PING_REQUEST_PACKET) {
            throw new IOException("Received invalid ping response packet");
        }
        long pongToken = input.readLong(); // Read response token
        if (pongToken != PING_TOKEN) {
            throw new IOException("Received mangled ping response packet");
        }

        return (int) TimeUnit.NANOSECONDS.toMillis(timeReceived - timeSent);
    }

    private PingResponse status(boolean ping) throws IOException {
        output.writeByte(0x01); // Size of packet
        output.writeByte(STATUS_REQUEST_PACKET);

        ByteUtils.readVarInt(input); // Read and ignore packet size
        int id = ByteUtils.readVarInt(input);
        if (id != STATUS_REQUEST_PACKET) {
            throw new IOException("Received invalid status response packet");
        }

        int length = ByteUtils.readVarInt(input);
        if (length < 1) {
            throw new IOException("Received invalid status response");
        }

        byte[] data = new byte[length];
        input.readFully(data);
        String json = new String(data, StandardCharsets.UTF_8);

        int latency = (ping ? ping() : -1);

        return parsePingResponse(new JsonParser().parse(json).getAsJsonObject(), address, latency);
    }

    @Override
    public void close() throws IOException {
        socket.close();
        input.close();
        output.close();
    }


    private static PingResponse parsePingResponse(JsonObject response, Address address, int latency) {
        JsonElement faviconElem = response.get("favicon");
        String favicon = (faviconElem != null ? faviconElem.getAsString() : null);

        String descriptionText = null, descriptionExtra = null;
        JsonElement descriptionElem = response.get("description");
        if (descriptionElem != null) {
            if (descriptionElem.isJsonObject()) {
                JsonElement descriptionTextElem = descriptionElem.getAsJsonObject().get("text");
                if (descriptionTextElem != null) {
                    descriptionText = descriptionTextElem.getAsString();
                }
                JsonElement descriptionExtraElem = descriptionElem.getAsJsonObject().get("extra");
                if (descriptionExtraElem != null) {
                    descriptionExtra = descriptionExtraElem.toString();
                }
            } else {
                descriptionText = descriptionElem.getAsString();
            }
        }
        Description description = new Description(descriptionText, descriptionExtra);

        JsonObject versionObject = response.getAsJsonObject("version");
        String versionName = versionObject.get("name").getAsString();
        int versionProtocol = versionObject.get("protocol").getAsInt();
        Version version = new Version(versionName, versionProtocol);

        JsonObject playersObject = response.getAsJsonObject("players");
        int playerMax = playersObject.get("max").getAsInt();
        int playerCount = playersObject.get("online").getAsInt();
        List<PingResponse.Player> playerSample = new ArrayList<>();
        JsonArray playerSampleArray = playersObject.getAsJsonArray("sample");
        if (playerSampleArray != null) {
            for (JsonElement playerElement : playerSampleArray) {
                JsonObject playerObject = playerElement.getAsJsonObject();
                String idStr = playerObject.get("id").getAsString();
                if (idStr.length() == 32) {
                    idStr = idStr.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                }
                UUID id = UUID.fromString(idStr);
                String name = playerObject.get("name").getAsString();
                playerSample.add(new PingResponse.Player(id, name));
            }
        }

        PingResponse.PlayersStatus players = new PingResponse.PlayersStatus(playerMax, playerCount, playerSample);

        return new PingResponse(address, players, version, description, favicon, latency);
    }

}
