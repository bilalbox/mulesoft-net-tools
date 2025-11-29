package com.mulesoft.tool.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.util.Enumeration;
import java.util.Collections;

public class NetworkUtils {

	public static String ping(String host) throws Exception {
		return execute(new ProcessBuilder("ping", "-c", "4", host));
	}

	public static String resolveIPs(String host, String dnsServer) throws UnknownHostException {
		if (dnsServer.equals("default") || dnsServer == null || dnsServer.isEmpty())
 		{
			InetAddress[] addresses = InetAddress.getAllByName(host);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < addresses.length; i++) {
				if (i != 0) {
					sb.append("\n");
				}
				sb.append(addresses[i].getHostAddress());
			}
			return sb.toString();
		}	
		else {
 			dnsServer = "@" + dnsServer;
			try {
				return execute(new ProcessBuilder("dig", "+short", dnsServer, host));
			} catch (IOException e) {
				return e.getMessage();
			} 
		}
	}

	public static String curl(String url, String[] headers, Boolean insecure) throws IOException {
		//-i include protocol headers
		//-L follow redirects
		//-k insecure
		//-E cert status
		List<String> command = new ArrayList<String>();
		command.add("curl");
		if(insecure) command.add("-k");
		command.add("-i");
		command.add("-L");
		command.add(url);
		for (String header : headers ) {
			command.add("-H");
			command.add(header);
		}		
		return execute(new ProcessBuilder(command));
	}

	public static String testConnect(String host, String port) {
		long startTime = System.nanoTime();
		long totalTime = System.nanoTime();
		String result = "";
		for (int x = 1; x <= 5; x++) {
			try {
				Socket socket = new Socket();
				startTime = System.nanoTime();
				socket.connect(new InetSocketAddress(host, Integer.parseInt(port)), 10000);
				socket.setSoTimeout(10000);
				if (socket.isConnected()) {
					totalTime = System.nanoTime() - startTime;
					socket.getInputStream();
				}
				socket.close();
			} 
			catch (java.net.UnknownHostException e) {
				return "Could not resolve host " + host;
			}
			catch (java.net.SocketTimeoutException e) {
				return "Timeout while trying to connect to " + host;
			}
			catch (java.lang.IllegalArgumentException e) {
				return e.getMessage();
			}
			catch (Exception e) {
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				e.printStackTrace(new PrintStream(b));
				return b.toString();
			}
			result = result + "Probe " + x + ": Connection successful, RTT=" + Long.toString(totalTime/1000000) + "ms\n";
		}
		return result + "socket test completed";
	}

	public static String traceRoute(String host) throws Exception {
		return execute(new ProcessBuilder("traceroute", "-w", "3", "-q", "1", "-m", "18", "-n", host));
	}

	public static String certest(String host, String port) throws Exception {
		return execute(new ProcessBuilder("openssl", "s_client", "-showcerts", "-servername", host, "-connect", host+":"+port));
	}

	public static String cipherTest(String host, String port) throws Exception {
		String remoteEndpointSupportedCiphers = "List of supported ciphers:\n\n";
		String[] openSslAvailableCiphers = execute(new ProcessBuilder("openssl","ciphers","ALL:!eNULL")).split(":");

		for (String cipher : openSslAvailableCiphers) {
			if (execute(new ProcessBuilder("openssl", "s_client", "-cipher", cipher, "-servername", host, "-connect", host+":"+port)).contains("BEGIN CERTIFICATE")) {
				remoteEndpointSupportedCiphers = remoteEndpointSupportedCiphers + cipher + ": YES\n";
			} else {
				remoteEndpointSupportedCiphers = remoteEndpointSupportedCiphers + cipher + ": NO\n";
			}
		}
		return remoteEndpointSupportedCiphers;
	}

	public static String getNetworkConfiguration() {
        StringBuilder sb = new StringBuilder();
        
        // Get network interfaces info (IP and subnet)
        sb.append("=== Network Interfaces ===\n");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isUp() && !iface.isLoopback()) {
                    sb.append("\nInterface: ").append(iface.getDisplayName()).append("\n");
                    for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                        if (addr.getAddress() != null) {
                            sb.append("  IP Address: ").append(addr.getAddress().getHostAddress()).append("\n");
                            sb.append("  Prefix Length: /").append(addr.getNetworkPrefixLength());
                            sb.append(" (").append(prefixToSubnetMask(addr.getNetworkPrefixLength())).append(")\n");
                            if (addr.getBroadcast() != null) {
                                sb.append("  Broadcast: ").append(addr.getBroadcast().getHostAddress()).append("\n");
                            }
                        }
                    }
                    byte[] mac = iface.getHardwareAddress();
                    if (mac != null) {
                        sb.append("  MAC: ").append(formatMac(mac)).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("Error getting interfaces: ").append(e.getMessage()).append("\n");
        }
        
        // Get default gateway
        sb.append("\n=== Default Gateway ===\n");
        try {
            String gateway = execute(new ProcessBuilder("ip", "route", "show", "default"));
            sb.append(gateway.isEmpty() ? "Not found\n" : gateway);
        } catch (IOException e) {
            sb.append("Error: ").append(e.getMessage()).append("\n");
        }
    
        
        return sb.toString();
    }

    private static String prefixToSubnetMask(short prefixLength) {
        if (prefixLength > 32 || prefixLength < 0) {
            return "N/A (IPv6)";
        }
        int mask = prefixLength == 0 ? 0 : 0xFFFFFFFF << (32 - prefixLength);
        return String.format("%d.%d.%d.%d",
            (mask >> 24) & 0xFF,
            (mask >> 16) & 0xFF,
            (mask >> 8) & 0xFF,
            mask & 0xFF);
    }

    private static String formatMac(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X", mac[i]));
            if (i < mac.length - 1) sb.append(":");
        }
        return sb.toString();
    }

	private static String execute(ProcessBuilder pb) throws IOException {
		Process p = pb.start();
		OutputStream stdin = p.getOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
		writer.write("\n");
        writer.flush();
        writer.close();
		SequenceInputStream sis = new SequenceInputStream(p.getInputStream(), p.getErrorStream());
		java.util.Scanner s = new java.util.Scanner(sis).useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
	}
}
