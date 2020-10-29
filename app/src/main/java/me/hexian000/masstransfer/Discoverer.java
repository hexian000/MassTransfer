package me.hexian000.masstransfer;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

final class Discoverer {
	private final static int BROADCAST_INTERVAL = 100;
	private final static int BROADCAST_TIMEOUT = 500;
	private final byte[] magic = "MassTransfer".getBytes();
	private final int port;
	private final Map<String, Long> peers;
	private List<AnnounceInterface> announce;
	private Timer timer = null;

	Discoverer(int port) {
		this.port = port;
		peers = Collections.synchronizedMap(new HashMap<>());
		announce = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> i = NetworkInterface.getNetworkInterfaces(); i.hasMoreElements(); ) {
				NetworkInterface nic = i.nextElement();
				if (nic.isLoopback() || !nic.isUp()) {
					continue;
				}
				for (InterfaceAddress interfaceAddress : nic.getInterfaceAddresses()) {
					InetAddress broadcast = interfaceAddress.getBroadcast();
					if (broadcast == null) {
						continue;
					}
					Log.d(MassTransfer.LOG_TAG, "broadcast init at " + broadcast.toString());
					DatagramSocket socket = new DatagramSocket(port);
					socket.setBroadcast(true);
					AnnounceInterface item = new AnnounceInterface();
					item.broadcast = broadcast;
					item.socket = socket;
					announce.add(item);
				}
			}
		} catch (IOException e) {
			Log.e(MassTransfer.LOG_TAG, "broadcast init error", e);
		}
	}

	public Set<String> getPeers() {
		return peers.keySet();
	}

	void start() {
		if (announce == null || timer != null) {
			return;
		}
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					for (AnnounceInterface item : announce) {
						DatagramPacket packet = new DatagramPacket(magic, magic.length, item.broadcast, port);
						item.socket.send(packet);
					}
				} catch (IOException e) {
					Log.e(MassTransfer.LOG_TAG, "broadcast error", e);
				}
				long now = System.currentTimeMillis();
				peers.entrySet().removeIf(entry -> now - entry.getValue() > BROADCAST_TIMEOUT);
			}
		}, 0, BROADCAST_INTERVAL);
		for (AnnounceInterface item : announce) {
			new Thread(() -> {
				byte[] buffer = new byte[magic.length];
				DatagramPacket p = new DatagramPacket(buffer, buffer.length);
				while (true) {
					try {
						item.socket.receive(p);
						if (Arrays.equals(magic, buffer)) {
							InetAddress ip = p.getAddress();
							if (!ip.isAnyLocalAddress() && !ip.isLoopbackAddress() && NetworkInterface
									.getByInetAddress(ip) == null) {
								peers.put(ip.getHostAddress(), System.currentTimeMillis());
							}
						}
					} catch (IOException e) {
						if (item.socket.isClosed()) {
							break;
						}
						Log.e(MassTransfer.LOG_TAG, "broadcast receive error", e);
					}
				}
				Log.d(MassTransfer.LOG_TAG, "broadcast receiver exited");
			}).start();
		}
	}

	void close() {
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		if (announce != null) {
			for (AnnounceInterface item : announce) {
				item.socket.close();
			}
			announce = null;
		}
		peers.clear();
	}

	private static class AnnounceInterface {
		InetAddress broadcast;
		DatagramSocket socket;
	}
}
