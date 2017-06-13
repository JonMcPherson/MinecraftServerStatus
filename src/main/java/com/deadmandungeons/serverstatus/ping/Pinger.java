package com.deadmandungeons.serverstatus.ping;

import com.deadmandungeons.serverstatus.MinecraftServer;

import java.io.IOException;
import java.net.ConnectException;

/**
 * A Pinger is a client that implements the Server List Ping (SLP) protocol
 * @see <a href="http://wiki.vg/Server_List_Ping">SLP protocol</a>
 */
public interface Pinger {

    /**
     * Ping the target Minecraft server.
     * @return the latency in milliseconds determined by the ping from this machine to the server and back
     * @throws ConnectException if an error occurs connecting to the server
     * @throws IOException if an error occurs communicating with the server
     */
    int ping() throws IOException;

    /**
     * Retrieve information on the target Minecraft server.
     * @return a MinecraftServer instance containing server information
     * @throws ConnectException if an error occurs connecting to the server
     * @throws IOException if an error occurs communicating with the server
     */
    MinecraftServer pingServer() throws IOException;

    /**
     * Retrieve information on the target Minecraft server including its status and the ping latency.
     * @return a PingResponse instance containing server status information including the ping latency
     * @throws ConnectException if an error occurs connecting to the server
     * @throws IOException if an error occurs communicating with the server
     */
    PingResponse pingServerStatus() throws IOException;

}
