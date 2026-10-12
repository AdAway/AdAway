package org.adaway.vpn.dns;

import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.POLLERR;
import static android.system.OsConstants.POLLHUP;
import static android.system.OsConstants.POLLNVAL;

import android.os.ParcelFileDescriptor;
import android.os.Build;
import android.system.StructPollfd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.function.Consumer;

import timber.log.Timber;

/**
 * This class represents a DNS query.
 *
 * @author Bruce BUJON
 */
class DnsQuery extends PendingDnsQuery {
    /**
     * The socket used to query DNS server.
     */
    private final DatagramSocket socket;
    /**
     * The callback to call with the query response data.
     */
    private final Consumer<byte[]> callback;
    /**
     * The pollfd related to the query to poll the OS with.
     */
    private final StructPollfd pollfd;
    private final ParcelFileDescriptor socketDescriptor;
    private final ParcelFileDescriptor legacySocketReference;
    /**
     * The query creation time from the monotonic clock, in seconds.
     */
    private final long time;

    /**
     * Constructor.
     *
     * @param socket   The socket used to query DNS server.
     * @param callback The callback to call with the query response data.
     */
    DnsQuery(DatagramSocket socket, Consumer<byte[]> callback, long time) throws IOException {
        this.socket = socket;
        this.callback = callback;
        this.time = time;
        this.pollfd = new StructPollfd();
        ParcelFileDescriptor reference = ParcelFileDescriptor.fromDatagramSocket(this.socket);
        if (reference == null) {
            throw new IOException("DNS socket has no file descriptor.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Pre-29 returns an alias. Retain it so its finalizer cannot close the socket.
            this.legacySocketReference = reference;
            try {
                this.socketDescriptor = reference.dup();
            } catch (IOException e) {
                this.socket.close();
                reference.close();
                throw e;
            }
        } else {
            this.legacySocketReference = null;
            this.socketDescriptor = reference;
        }
        this.pollfd.fd = this.socketDescriptor.getFileDescriptor();
        this.pollfd.events = (short) POLLIN;
    }

    /**
     * Check whether a query is older than a timestamp.
     *
     * @param timestamp The monotonic timestamp (in seconds) to compare query time with.
     * @return <code>true</code> if the query is older than the given timestamp, <code>false</code> otherwise.
     */
    @Override
    boolean isOlderThan(long timestamp) {
        return this.time < timestamp;
    }

    /**
     * Get the pollfd related to the query to poll the OS with.
     * @return The pollfd related to the query to poll the OS with.
     */
    @Override
    StructPollfd getPollfd() {
        return this.pollfd;
    }

    /**
     * Check whether the query is answered, meaning its socket has received data to read.
     *
     * @return {@code true} if there is data to read from {@link #socket}, {@code false} otherwise.
     */
    @Override
    boolean isAnswered() {
        return (this.pollfd.revents & (POLLIN | POLLERR | POLLHUP | POLLNVAL)) != 0;
    }

    /**
     * Read DNS query response and notify callback.
     */
    @Override
    void handleResponse() {
        try {
            if ((this.pollfd.revents & POLLIN) == 0) {
                return;
            }
            this.callback.accept(receiveResponse(this.socket));
        } catch (IOException e) {
            Timber.w(e, "Could not handle DNS response.");
        } finally {
            close();
        }
    }

    static byte[] receiveResponse(DatagramSocket socket) throws IOException {
        byte[] responseData = new byte[65535];
        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length);
        socket.receive(responsePacket);
        return Arrays.copyOf(responseData, responsePacket.getLength());
    }

    @Override
    public void close() {
        this.socket.close();
        closeDescriptor(this.socketDescriptor);
        // The legacy reference shares the socket's now-invalid FileDescriptor. Retire its
        // close guard only after the socket owner has closed it; never detach the alias.
        if (this.legacySocketReference != null) {
            closeDescriptor(this.legacySocketReference);
        }
    }

    private static void closeDescriptor(ParcelFileDescriptor descriptor) {
        try {
            descriptor.close();
        } catch (IOException e) {
            Timber.w(e, "Could not close DNS query descriptor.");
        }
    }
}
