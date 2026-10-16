package com.company.attendance.device;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ZktecoPullClientTest {
    private static ByteBuffer le(byte[] b) { return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN); }
    @Test void downloadsAttendanceOverTcpWithFragmentedFrames() throws Exception {
        byte[] data = new byte[44]; le(data).putInt(40); data[6] = '7';
        long encoded = (((26L * 12 + 9) * 31 + 15) * 24 + 8) * 3600;
        le(data).putInt(31, (int)encoded);
        try (ServerSocket server = new ServerSocket(0)) {
            var worker = Executors.newSingleThreadExecutor();
            try {
                Future<?> done = worker.submit(() -> {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(5000);
                        var in = new DataInputStream(socket.getInputStream());
                        for (int step = 0; step < 3; step++) {
                            byte[] h = new byte[8]; in.readFully(h);
                            byte[] request = new byte[le(h).getInt(4)]; in.readFully(request);
                            assertThat(Short.toUnsignedInt(le(request).getShort())).isEqualTo(new int[]{1000,1503,1001}[step]);
                            byte[] payload = step == 1 ? data : new byte[0];
                            byte[] frame = ByteBuffer.allocate(16 + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(0x7d825050).putInt(8 + payload.length).putShort((short)(step == 1 ? 1501 : 2000))
                                .putShort((short)0).putShort((short)42).putShort((short)step).put(payload).array();
                            for (byte b : frame) { socket.getOutputStream().write(b); socket.getOutputStream().flush(); }
                        }
                    } catch (IOException ex) { throw new UncheckedIOException(ex); }
                });
                var punches = new ZktecoPullClient().read("127.0.0.1", server.getLocalPort());
                assertThat(punches).hasSize(1);
                assertThat(punches.getFirst().pin()).isEqualTo("7");
                assertThat(punches.getFirst().time()).isEqualTo("2026-10-16T08:00:00");
                done.get(10, TimeUnit.SECONDS);
            } finally { worker.shutdownNow(); }
        }
    }
    @Test void rejectsUnsupportedOrTruncatedRecords() {
        assertThatThrownBy(() -> ZktecoPullClient.decode(new byte[3])).isInstanceOf(IOException.class);
        byte[] other = new byte[20]; le(other).putInt(16);
        assertThatThrownBy(() -> ZktecoPullClient.decode(other)).hasMessageContaining("Unsupported");
    }
}
