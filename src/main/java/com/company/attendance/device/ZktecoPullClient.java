package com.company.attendance.device;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

/** Read-only ZK TCP protocol adapter. Supports the 40-byte attendance format. */
@Component
public class ZktecoPullClient {
    private static final int MAX = 16 * 1024 * 1024;
    public record Punch(String pin, LocalDateTime time) {}
    private record Packet(int command, byte[] data) {}
    private static ByteBuffer le(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }

    public List<Punch> read(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(10000);
            Session session = new Session(socket);
            session.expect(session.command(1000, new byte[0]), 2000);
            try {
                byte[] request = new byte[11]; request[0] = 1; request[1] = 13;
                Packet response = session.command(1503, request);
                byte[] data;
                if (response.command == 1501) data = response.data;
                else {
                    session.expect(response, 2000);
                    if (response.data.length < 5) throw new IOException("Missing attendance buffer size");
                    int size = le(response.data).getInt(1);
                    if (size < 4 || size > MAX) throw new IOException("Invalid attendance buffer size");
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    for (int offset = 0; offset < size;) {
                        int length = Math.min(32768, size - offset);
                        byte[] range = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(offset).putInt(length).array();
                        Packet part = session.command(1504, range);
                        if (part.command == 1500) {
                            int received = 0;
                            while (received < length) {
                                part = session.receive(); session.expect(part, 1501);
                                received += part.data.length; buffer.write(part.data);
                                if (part.data.length == 0 || received > length) throw new IOException("Invalid data chunk");
                            }
                            session.expect(session.receive(), 2000);
                        } else {
                            session.expect(part, 1501);
                            if (part.data.length != length) throw new IOException("Incomplete data chunk");
                            buffer.write(part.data);
                        }
                        offset += length;
                    }
                    session.expect(session.command(1502, new byte[0]), 2000);
                    data = buffer.toByteArray();
                }
                return decode(data);
            } finally {
                try { session.command(1001, new byte[0]); } catch (IOException ignored) { }
            }
        }
    }

    static List<Punch> decode(byte[] data) throws IOException {
        if (data.length < 4) throw new IOException("Incomplete attendance data");
        int size = le(data).getInt();
        if (size != data.length - 4 || size % 40 != 0)
            throw new IOException("Unsupported attendance format (requires 40-byte ZK records)");
        List<Punch> result = new ArrayList<>();
        try {
            for (int offset = 4; offset < data.length; offset += 40) {
                int end = offset + 2;
                while (end < offset + 26 && data[end] != 0) end++;
                String pin = new String(data, offset + 2, end - offset - 2, StandardCharsets.UTF_8);
                long t = Integer.toUnsignedLong(le(data).getInt(offset + 27));
                int second = (int)(t % 60); t /= 60;
                int minute = (int)(t % 60); t /= 60;
                int hour = (int)(t % 24); t /= 24;
                int day = (int)(t % 31) + 1; t /= 31;
                int month = (int)(t % 12) + 1; t /= 12;
                if (pin.isBlank()) throw new IOException("Empty device PIN");
                result.add(new Punch(pin, LocalDateTime.of((int)t + 2000, month, day, hour, minute, second)));
            }
        } catch (java.time.DateTimeException ex) { throw new IOException("Invalid device timestamp", ex); }
        result.sort(Comparator.comparing(Punch::time));
        return result;
    }

    private static class Session {
        final Socket socket;
        int session, reply = 65534;
        Session(Socket socket) { this.socket = socket; }
        Packet command(int command, byte[] data) throws IOException {
            byte[] packet = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short)command).putShort((short)0).putShort((short)session).putShort((short)reply).put(data).array();
            int sum = 0;
            for (int i = 0; i < packet.length; i += 2) {
                sum += (packet[i] & 255) + (i + 1 < packet.length ? (packet[i + 1] & 255) << 8 : 0);
                if (sum > 65535) sum -= 65535;
            }
            le(packet).putShort(2, (short)(65534 - sum));
            reply = (reply + 1) % 65535; le(packet).putShort(6, (short)reply);
            OutputStream out = socket.getOutputStream();
            out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x7d825050).putInt(packet.length).array());
            out.write(packet); out.flush();
            return receive();
        }
        Packet receive() throws IOException {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            byte[] header = new byte[8]; in.readFully(header);
            int size = le(header).getInt(4);
            if (le(header).getInt() != 0x7d825050 || size < 8 || size > MAX) throw new IOException("Invalid ZK TCP frame");
            byte[] packet = new byte[size]; in.readFully(packet);
            session = Short.toUnsignedInt(le(packet).getShort(4)); reply = Short.toUnsignedInt(le(packet).getShort(6));
            return new Packet(Short.toUnsignedInt(le(packet).getShort()), Arrays.copyOfRange(packet, 8, size));
        }
        void expect(Packet packet, int command) throws IOException {
            if (packet.command != command) throw new IOException(packet.command == 2005
                ? "Reader requires a communication key; this adapter supports key 0 only."
                : "Unsupported reader response: " + packet.command);
        }
    }
}
