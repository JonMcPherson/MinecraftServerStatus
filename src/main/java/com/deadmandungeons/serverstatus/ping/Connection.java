package com.deadmandungeons.serverstatus.ping;

import com.deadmandungeons.serverstatus.InetServerAddress;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class Connection implements AutoCloseable {

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final int latency;

    public static class Connector {

        private int timeout;
        private boolean tcpNoDelay;
        private boolean buffered;

        private final InetAddress address;
        private final int port;

        public Connector(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        public Connector timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Connector tcpNoDelay() {
            tcpNoDelay = true;
            return this;
        }

        public Connector buffered() {
            buffered = true;
            return this;
        }

        public Connection connect() throws ConnectException {
            try {
                Socket socket = new Socket();
                // Socket may be closed and immediately rebound/reconnected
                socket.setReuseAddress(true);
                socket.setSoTimeout(timeout);
                socket.setTcpNoDelay(tcpNoDelay);

                // Roughly determine latency from TCP 3-way handshake
                long startTime = System.nanoTime();
                socket.connect(new InetSocketAddress(address, port), timeout);
                int latency = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                if (buffered) {
                    input = new BufferedInputStream(input);
                    output = new BufferedOutputStream(output);
                }

                return new Connection(socket, input, output, latency);
            } catch (ConnectException e) {
                throw e;
            } catch (IOException e) {
                throw (ConnectException) new ConnectException("failed to establish connection").initCause(e);
            }
        }

    }

    public static Connector to(InetAddress address, int port) {
        return new Connector(address, port);
    }

    public static Connector to(InetServerAddress address) {
        return to(address.getInetAddress(), address.getPort());
    }

    private Connection(Socket socket, InputStream input, OutputStream output, int latency) {
        this.socket = socket;
        this.input = new DataInputStream(input);
        this.output = new DataOutputStream(output);
        this.latency = latency;
    }

    public Socket getSocket() {
        return socket;
    }

    public DataInputStream getInputStream() {
        return input;
    }

    public DataOutputStream getOutputStream() {
        return output;
    }

    public int getLatency() {
        return latency;
    }


    public RequestPacket createPacket() {
        return new RequestPacket();
    }

    public ResponsePacket readPacket() throws IOException {
        int length = readVarInt(input);
        long timeReceived = System.nanoTime();

        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new ResponsePacket(bytes, timeReceived);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }


    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int remaining = value;
        for (int i = 0; i < 5; i++) {
            if ((remaining & ~0x7F) == 0) {
                out.writeByte(remaining);
                return;
            }
            out.writeByte(remaining & 0x7F | 0x80);
            remaining >>>= 7;
        }
        throw new IllegalArgumentException("The value " + value + " is too big to send in a varint");
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            int part = in.readByte();
            result |= (part & 0x7F) << 7 * i;
            if ((part & 0x80) != 128) {
                return result;
            }
        }
        throw new IOException("Server sent a varint that was too big!");
    }

    public class RequestPacket extends DataOutputStream {

        private RequestPacket() {
            super(new ByteArrayOutputStream());
        }

        public void writeVarInt(int value) throws IOException {
            Connection.writeVarInt(this, value);
        }

        public void writeVarUTF(String value) throws IOException {
            writeVarInt(value.length());
            write(value.getBytes(StandardCharsets.UTF_8));
        }

        public long send() throws IOException {
            byte[] bytes = ((ByteArrayOutputStream) out).toByteArray();

            long timeSent = System.nanoTime();
            Connection.writeVarInt(output, bytes.length);
            output.write(bytes);
            output.flush();
            return timeSent;
        }

    }

    public class ResponsePacket extends DataInputStream {

        private final long timeReceived;

        private ResponsePacket(byte[] bytes, final long timeReceived) throws IOException {
            super(new ByteArrayInputStream(bytes));
            this.timeReceived = timeReceived;
        }

        public long getTimeReceived() {
            return timeReceived;
        }


        public int readVarInt() throws IOException {
            return Connection.readVarInt(this);
        }

        public String readVarUTF() throws IOException {
            int length = readVarInt();
            byte[] bytes = new byte[length];
            readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

    }

}
