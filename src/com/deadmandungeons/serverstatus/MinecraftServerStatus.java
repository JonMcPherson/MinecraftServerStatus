package com.deadmandungeons.serverstatus;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.deadmandungeons.serverstatus.ServerInfo.Address;
import com.deadmandungeons.serverstatus.ServerInfo.Version;
import com.deadmandungeons.serverstatus.ServerStatusPing.Player;
import com.deadmandungeons.serverstatus.ServerStatusPing.PlayersStatus;
import com.deadmandungeons.serverstatus.ServerStatusQuery.PlayersList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class MinecraftServerStatus {
	
	public static ServerStatusPing pingServerStatus(String host, int port) throws IOException {
		InetAddress address = InetAddress.getByName(host);
		
		return pingServerStatus(address, port);
	}
	
	public static ServerStatusPing pingServerStatus(InetAddress address, int port) throws IOException {
		return MinecraftPing.pingServerStatus(address, port);
	}
	
	public ServerStatusQuery queryServerStatus(String host, int port) throws IOException {
		InetAddress address = InetAddress.getByName(host);
		
		return queryServerStatus(address, port);
	}
	
	public ServerStatusQuery queryServerStatus(InetAddress address, int port) throws IOException {
		return MinecraftQuery.queryServerStatus(address, port);
	}
	
	
	private static class MinecraftPing {
		
		private static final byte TCP_HANDSHAKE_PACKET = 0x00;
		private static final byte TCP_STATUS_REQUEST_PACKET = 0x00;
		private static final byte TCP_PROTOCOL_VERSION = 4;
		private static final byte TCP_STATUS_HANDSHAKE = 1;
		private static final int TIMEOUT = 2000;
		private static final Gson GSON = new GsonBuilder().registerTypeAdapter(ServerStatusPing.class, new ServerStatusDeserializer()).create();
		
		private static ServerStatusPing pingServerStatus(InetAddress address, int port) throws IOException {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(address, port), TIMEOUT);
				
				try (DataInputStream in = new DataInputStream(socket.getInputStream());
						DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
					ByteArrayOutputStream handshake_bytes = new ByteArrayOutputStream();
					DataOutputStream handshake = new DataOutputStream(handshake_bytes);
					
					handshake.writeByte(TCP_HANDSHAKE_PACKET);
					ByteUtils.writeVarInt(handshake, TCP_PROTOCOL_VERSION);
					ByteUtils.writeVarInt(handshake, address.getHostAddress().length());
					handshake.writeBytes(address.getHostAddress());
					handshake.writeShort(port);
					ByteUtils.writeVarInt(handshake, TCP_STATUS_HANDSHAKE);
					
					ByteUtils.writeVarInt(out, handshake_bytes.size());
					out.write(handshake_bytes.toByteArray());
					
					// Status request
					
					out.writeByte(0x01); // Size of packet
					out.writeByte(TCP_STATUS_REQUEST_PACKET);
					
					// Status response
					
					ByteUtils.readVarInt(in); // Read and ignore packet size
					int id = ByteUtils.readVarInt(in);
					
					if (id == -1) {
						throw new IOException("Server prematurely ended stream.");
					}
					if (id != TCP_STATUS_REQUEST_PACKET) {
						throw new IOException("Server returned invalid packet.");
					}
					
					int length = ByteUtils.readVarInt(in);
					if (length == -1) {
						throw new IOException("Server prematurely ended stream.");
					}
					if (length == 0) {
						throw new IOException("Server returned unexpected value.");
					}
					
					byte[] data = new byte[length];
					in.readFully(data);
					String json = new String(data, "UTF-8");
					
					ServerStatusPing status = GSON.fromJson(json, ServerStatusPing.class);
					
					return new ServerStatusPing(new Address(address.getHostAddress(), port), status.getPlayers(), status.getVersion(),
							status.getDescription(), status.getFavicon());
				}
			}
		}
		
		
		private static class ServerStatusDeserializer implements JsonDeserializer<ServerStatusPing> {
			
			@Override
			public ServerStatusPing deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				JsonObject jsonObject = json.getAsJsonObject();
				
				String favicon = jsonObject.get("favicon").getAsString();
				String description = jsonObject.getAsJsonObject("description").get("text").getAsString();
				
				JsonObject versionObject = jsonObject.getAsJsonObject("version");
				String versionName = versionObject.get("name").getAsString();
				int versionProtocol = versionObject.get("protocol").getAsInt();
				Version version = new Version(versionName, versionProtocol);
				
				JsonObject playersObject = jsonObject.getAsJsonObject("players");
				int playerMax = playersObject.get("max").getAsInt();
				int playerCount = playersObject.get("online").getAsInt();
				List<Player> playerSample = new ArrayList<>();
				for (JsonElement playerElement : playersObject.getAsJsonArray("sample")) {
					JsonObject playerObject = playerElement.getAsJsonObject();
					UUID id = UUID.fromString(playerObject.get("id").getAsString());
					String name = playerObject.get("name").getAsString();
					playerSample.add(new Player(id, name));
				}
				PlayersStatus players = new PlayersStatus(playerMax, playerCount, playerSample);
				
				return new ServerStatusPing(null, players, version, description, favicon);
			}
			
		}
		
	}
	
	
	private static class MinecraftQuery {
		
		private static final byte[] UDP_MAGIC = { (byte) 0xFE, (byte) 0xFD };
		private static final byte HANDSHAKE_REQUEST_TYPE = 9;
		private static final byte STAT_REQUEST_TYPE = 0;
		private static final int SESSION_ID = 1;
		private static final int TIMEOUT = 2000;
		
		
		private static ServerStatusQuery queryServerStatus(InetAddress address, int port) throws IOException {
			byte[] handshakeRequest = createRequest(HANDSHAKE_REQUEST_TYPE, SESSION_ID, new byte[0]);
			
			// should be 11 bytes total
			int val = 11 - handshakeRequest.length;
			
			try (DatagramSocket socket = createSocket()) {
				handshakeRequest = ByteUtils.padArrayEnd(handshakeRequest, val);
				
				DatagramPacket handshakePacket = new DatagramPacket(handshakeRequest, handshakeRequest.length, address, port);
				byte[] handshakeResponse = sendUDP(socket, handshakePacket);
				
				int token = Integer.parseInt(new String(handshakeResponse, "UTF-8").trim());
				
				byte[] payload = ByteUtils.padArrayEnd(ByteUtils.intToBytes(token), 4);
				byte[] queryRequest = createRequest(STAT_REQUEST_TYPE, SESSION_ID, payload);
				
				DatagramPacket queryPacket = new DatagramPacket(queryRequest, queryRequest.length, address, port);
				byte[] queryResponse = sendUDP(socket, queryPacket);
				
				return parseQueryResponse(queryResponse);
			}
		}
		
		
		private static byte[] sendUDP(DatagramSocket socket, DatagramPacket requestPacket) throws IOException {
			socket.send(requestPacket);
			
			// receive a response in a new packet
			byte[] out = new byte[9999];
			DatagramPacket responsePacket = new DatagramPacket(out, out.length);
			socket.setSoTimeout(TIMEOUT);
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
		
		private static ServerStatusQuery parseQueryResponse(byte[] response) {
			response = ByteUtils.trim(response);
			byte[][] data = ByteUtils.split(response);
			
			String description = new String(data[3]);
			// String gameMode = new String(data[5]); // Hardcoded to SMP
			// String gameId = new String(data[7]); // Hardcoded to MINECRAFT
			String versionName = new String(data[9]);
			Version version = new Version(versionName, 0); // TODO protocol is not returned in response but could mapped to version name
			
			String mapName = new String(data[13]);
			int playerCount = Integer.parseInt(new String(data[15]));
			int playerMax = Integer.parseInt(new String(data[17]));
			
			int addressPort = Short.parseShort(new String(data[19]));
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
			PlayersList players = new PlayersList(playerMax, playerCount, playerList);
			
			String favicon = null; // TODO favicon is needed for ServerInfo, but is not returned in query response...
			
			return new ServerStatusQuery(address, players, version, description, favicon, mapName, serverType, plugins);
		}
		
	}
	
}
