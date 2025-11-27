package com.xiaozhi.communication.server.mqtt;

import com.xiaozhi.communication.common.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class UdpAudioChannel {

    private static final Logger logger = LoggerFactory.getLogger(UdpAudioChannel.class);
    private static final int NONCE_LENGTH = 16;
    private static final int MAX_BIND_TRY = 32;

    private final DatagramSocket socket;
    private final byte[] keyBytes;
    private final byte[] baseNonce;
    private final SecretKeySpec keySpec;
    private final String sessionId;
    private final SessionManager sessionManager;
    private final BiConsumer<String, byte[]> audioConsumer;
    private final AtomicBoolean ready = new AtomicBoolean(true);
    private final AtomicInteger outboundSequence = new AtomicInteger();
    private volatile InetSocketAddress remoteAddress;

    private UdpAudioChannel(DatagramSocket socket,
                            byte[] keyBytes,
                            byte[] baseNonce,
                            String sessionId,
                            SessionManager sessionManager,
                            BiConsumer<String, byte[]> audioConsumer) {
        this.socket = socket;
        this.keyBytes = keyBytes;
        this.baseNonce = baseNonce;
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.sessionId = sessionId;
        this.sessionManager = sessionManager;
        this.audioConsumer = audioConsumer;
        startListener();
    }

    public static UdpAudioChannel create(String sessionId,
                                         MqttProperties properties,
                                         SessionManager sessionManager,
                                         BiConsumer<String, byte[]> audioConsumer) throws IOException {
        DatagramSocket socket = null;
        boolean bound = false;
        IOException lastException = null;
        for (int tries = 0; tries < MAX_BIND_TRY && !bound; tries++) {
            int port = properties.pickRandomUdpPort();
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(properties.getHost(), port));
                bound = true;
            } catch (IOException ex) {
                lastException = ex;
                if (socket != null) {
                    socket.close();
                }
            }
        }
        if (!bound || socket == null) {
            throw lastException != null ? lastException :
                    new SocketException("Unable to bind UDP port for session " + sessionId);
        }

        SecureRandom random = new SecureRandom();
        byte[] key = new byte[16];
        random.nextBytes(key);
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        nonce[0] = 0x01;
        nonce[1] = 0x00;
        return new UdpAudioChannel(socket, key, nonce, sessionId, sessionManager, audioConsumer);
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public String getKeyHex() {
        return HexFormat.of().withUpperCase().formatHex(keyBytes);
    }

    public String getBaseNonceHex() {
        return HexFormat.of().withUpperCase().formatHex(baseNonce);
    }

    public boolean isReady() {
        return ready.get() && !socket.isClosed();
    }

    public void setRemoteAddress(InetAddress address, int port) {
        this.remoteAddress = new InetSocketAddress(address, port);
    }

    public void sendToDevice(byte[] opusPayload) throws IOException, GeneralSecurityException {
        InetSocketAddress target = remoteAddress;
        if (target == null) {
            logger.debug("Remote UDP address missing, skip send - session {}", sessionId);
            return;
        }

        byte[] nonce = baseNonce.clone();
        putShort(nonce, 2, (short) opusPayload.length);
        long now = System.currentTimeMillis();
        putInt(nonce, 8, (int) (now & 0xFFFFFFFFL));
        putInt(nonce, 12, outboundSequence.incrementAndGet());

        byte[] cipher = crypt(opusPayload, nonce, Cipher.ENCRYPT_MODE);
        byte[] packetData = new byte[nonce.length + cipher.length];
        System.arraycopy(nonce, 0, packetData, 0, nonce.length);
        System.arraycopy(cipher, 0, packetData, nonce.length, cipher.length);

        DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
                target.getAddress(), target.getPort());
        socket.send(packet);
        sessionManager.updateLastActivity(sessionId);
    }

    public void close() {
        ready.set(false);
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private void startListener() {
        Thread.startVirtualThread(() -> {
            byte[] buffer = new byte[2048];
            while (isReady()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    remoteAddress = (InetSocketAddress) packet.getSocketAddress();
                    byte[] raw = packet.getData();
                    int len = packet.getLength();
                    if (len <= NONCE_LENGTH) {
                        continue;
                    }
                    byte[] nonce = new byte[NONCE_LENGTH];
                    System.arraycopy(raw, 0, nonce, 0, NONCE_LENGTH);
                    byte[] cipher = new byte[len - NONCE_LENGTH];
                    System.arraycopy(raw, NONCE_LENGTH, cipher, 0, cipher.length);
                    byte[] plain = crypt(cipher, nonce, Cipher.DECRYPT_MODE);
                    sessionManager.updateLastActivity(sessionId);
                    audioConsumer.accept(sessionId, plain);
                } catch (IOException ex) {
                    if (isReady()) {
                        logger.warn("UDP channel receive error - session {}", sessionId, ex);
                    }
                } catch (GeneralSecurityException ex) {
                    logger.error("Failed to decrypt UDP packet - session {}", sessionId, ex);
                }
            }
        });
    }

    private byte[] crypt(byte[] input, byte[] nonce, int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(mode, keySpec, new IvParameterSpec(nonce));
        return cipher.doFinal(input);
    }

    private void putShort(byte[] buffer, int offset, short value) {
        buffer[offset] = (byte) ((value >> 8) & 0xFF);
        buffer[offset + 1] = (byte) (value & 0xFF);
    }

    private void putInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) ((value >> 24) & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 16) & 0xFF);
        buffer[offset + 2] = (byte) ((value >> 8) & 0xFF);
        buffer[offset + 3] = (byte) (value & 0xFF);
    }
}

