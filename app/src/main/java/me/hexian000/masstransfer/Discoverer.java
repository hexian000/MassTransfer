package me.hexian000.masstransfer;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

final class Discoverer {
	private final byte[] magic = "MassTransfer".getBytes();
	private DatagramSocket announce;
	private int port;
	private Timer timer = null;
	private Map<String, Long> peers;

	Discoverer(int port) {
		this.port = port;
		peers = Collections.synchronizedMap(new HashMap<>());
		try {
			announce = new DatagramSocket(port);
			announce.setBroadcast(true);
		} catch (IOException e) {
			Log.e(TransferApp.LOG_TAG, "broadcast init error", e);
		}
	}

	public Set<String> getPeers() {
		return peers.keySet();
	}

	void start() {
		if (announce == null) return;
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					DatagramPacket packet = new DatagramPacket(magic,
							magic.length, InetAddress.getByName("255.255.255.255"),
							port);
					announce.send(packet);
				} catch (IOException e) {
					Log.e(TransferApp.LOG_TAG, "broadcast error", e);
				}
				long now = System.currentTimeMillis();
				peers.entrySet().removeIf(entry -> now - entry.getValue() > 4000);
			}
		}, 0, 2000);
		new Thread(() -> {
			byte[] buffer = new byte[magic.length];
			DatagramPacket p = new DatagramPacket(buffer, buffer.length);
			while (true) {
				try {
					announce.receive(p);
					if (Arrays.equals(magic, buffer)) {
						InetAddress ip = p.getAddress();
						if (!ip.isAnyLocalAddress() && !ip.isLoopbackAddress() &&
								NetworkInterface.getByInetAddress(ip) == null)
							peers.put(ip.getHostAddress(), System.currentTimeMillis());
					}
				} catch (IOException e) {
					if (announce == null || announce.isClosed())
						break;
					Log.e(TransferApp.LOG_TAG, "broadcast receive error", e);
				}
			}
			Log.d(TransferApp.LOG_TAG, "broadcast receiver exited");
		}).start();
	}

	void close() {
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		if (announce != null) {
			announce.close();
			announce = null;
		}
		peers.clear();
	}
}
