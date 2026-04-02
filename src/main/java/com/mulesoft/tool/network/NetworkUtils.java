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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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

	public static String getPublicIp() throws Exception {
		try {
			URL url = new URL("https://httpbin.org/ip");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(10000);
			connection.setReadTimeout(10000);
			
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				reader.close();
				
				// Parse JSON response to extract IP from { "origin": "118.99.117.167" }
				String jsonResponse = response.toString();
				String searchPattern = "\"origin\":";
				int startIndex = jsonResponse.indexOf(searchPattern);
				if (startIndex != -1) {
					startIndex += searchPattern.length();
					// Skip whitespace and opening quote
					while (startIndex < jsonResponse.length() && 
						   (jsonResponse.charAt(startIndex) == ' ' || jsonResponse.charAt(startIndex) == '"')) {
						startIndex++;
					}
					// Find closing quote
					int endIndex = jsonResponse.indexOf('"', startIndex);
					if (endIndex != -1) {
						return jsonResponse.substring(startIndex, endIndex);
					}
				}
				return "Could not parse IP from response: " + jsonResponse;
			} else {
				return "HTTP Error: " + responseCode + " - " + connection.getResponseMessage();
			}
		} catch (Exception e) {
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			e.printStackTrace(new PrintStream(b));
			return "Error retrieving public IP: " + e.getMessage() + "\n" + b.toString();
		}
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
