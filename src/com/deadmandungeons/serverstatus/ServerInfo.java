package com.deadmandungeons.serverstatus;

/**
 * A simple class containing basic server information that is mostly static and should not change often.
 */
public class ServerInfo {
	
	private final Address address;
	private final Players players;
	private final Version version;
	private final String description;
	private final String favicon;
	
	
	public ServerInfo(Address address, Players players, Version version, String description, String favicon) {
		this.address = address;
		this.players = players;
		this.version = version;
		this.description = description;
		this.favicon = favicon;
	}
	
	public ServerInfo(ServerInfo other) {
		address = new Address(other.getAddress().getIp(), other.getAddress().getPort());
		players = new Players(other.getPlayers().getMax());
		version = new Version(other.getVersion().getName(), other.getVersion().getProtocol());
		description = other.getDescription();
		favicon = other.getFavicon();
	}
	
	public Address getAddress() {
		return address;
	}
	
	public Players getPlayers() {
		return players;
	}
	
	public Version getVersion() {
		return version;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getFavicon() {
		return favicon;
	}
	
	
	public static class Address {
		
		private final String ip;
		private final int port;
		
		public Address(String ip, int port) {
			this.ip = ip;
			this.port = port;
		}
		
		public String getIp() {
			return ip;
		}
		
		public int getPort() {
			return port;
		}
		
	}
	
	public static class Players {
		
		private final int max;
		
		public Players(int max) {
			this.max = max;
		}
		
		public int getMax() {
			return max;
		}
		
	}
	
	public static class Version {
		
		private final String name;
		private final int protocol;
		
		public Version(String name, int protocol) {
			this.name = name;
			this.protocol = protocol;
		}
		
		public String getName() {
			return name;
		}
		
		public int getProtocol() {
			return protocol;
		}
		
	}
	
}
