import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Scanner;

import javax.swing.plaf.SliderUI;

public class Server {

	public static String multicasteRangeBaseIp = "235.1.1.";
	// The server socket.
	private static ServerSocket serverSocket = null;
	// The client socket.
	private static Socket clientSocket = null;
	// This server can accept clients' connections until it runs out of memory
	// or out of membership cards.
	private static int maxClientsCount = 100;
	public static final ClientThread[] threads = new ClientThread[maxClientsCount];

	// The list with subscribers per stream
	public static Hashtable<String, InetAddress> streams = null;
	// The list of publishers
	public static Hashtable<String, Socket> publishers = null;

	public static void main(String args[]) throws InterruptedException {
		long allocatedMemory = (Runtime.getRuntime().totalMemory() - Runtime
				.getRuntime().freeMemory());
		long presumableFreeMemory = Runtime.getRuntime().maxMemory()
				- allocatedMemory;

		// Default port 4444
		int portNumber = 4444;

		// Open a server socket on the portNumber (default 4444).
		try {
			serverSocket = new ServerSocket(portNumber);
		} catch (IOException e) {
			System.out.println(e);
		}

		// Initialization
		streams = new Hashtable<String, InetAddress>();
		publishers = new Hashtable<String, Socket>();

		// Greeting server user
		System.out
				.println("#############################################\n# Welcome to my publisher/subscriber server #\n#############################################");
		/*
		 * Create a client socket for each connection and pass it to a new
		 * client thread.
		 */
		while (true) {
			try {
				if (allocatedMemory > 3 * presumableFreeMemory) {
					PrintStream os = new PrintStream(
							clientSocket.getOutputStream());
					os.println("Server too busy. Try later.");
					os.close();
					clientSocket.close();
				} else {
					clientSocket = serverSocket.accept();
					maxClientsCount++;
					int i = 0;
					for (i = 0; i < maxClientsCount; i++) {
						if (threads[i] == null) {
							(threads[i] = new ClientThread(clientSocket, i))
									.start();
							break;
						} else {
							if (!threads[i].running) {
								(threads[i] = new ClientThread(clientSocket, i))
										.start();
								break;
							}
						}
					}
				}

			} catch (IOException e) {
				System.out.println(e);
			}

			allocatedMemory = (Runtime.getRuntime().totalMemory() - Runtime
					.getRuntime().freeMemory());
			presumableFreeMemory = Runtime.getRuntime().maxMemory()
					- allocatedMemory;
			Thread.sleep(500);
		}
	}

	public static class ClientThread extends Thread {

		private String ip = null;
		public DataInputStream is = null;
		public DataOutputStream os = null;
		public BufferedReader mesgIS = null;
		public PrintWriter mesgOS = null;
		public static MulticastSocket multScket = null;
		public InetAddress group = null;
		private Socket clientSocket = null;
		private int index;
		private String recentStream = null;
		private String outputLine;
		private boolean running = true;
		// Ip address for group creation
		private String multicastIpAdressGroup = null;
		public int portNumber = 4444;

		public ClientThread(Socket clientSocket, int index) throws IOException {
			this.clientSocket = clientSocket;
			this.ip = clientSocket.getRemoteSocketAddress().toString();
			this.index = index;
			this.is = new DataInputStream(clientSocket.getInputStream());
			this.os = new DataOutputStream(clientSocket.getOutputStream());
			this.mesgIS = new BufferedReader(new InputStreamReader(
					clientSocket.getInputStream()));
			this.mesgOS = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(clientSocket.getOutputStream())),
					true);
			this.multScket = new MulticastSocket(portNumber);
			this.multicastIpAdressGroup = multicasteRangeBaseIp + index;
		}

		public void run() {
			System.out.println("Thread:" + index + " ip:" + ip);
			mesgOS.println(" Welcome to my publisher/subscriber server");
			CommProtocol kkp = new CommProtocol();
			Scanner stdin = null;
			String cmdTemp = null;
			String stream_id = null;
			try {

				while (running) {
					if (mesgIS.ready()) {
						String msgFromClient = mesgIS.readLine();
						outputLine = kkp.processInput(msgFromClient);
						stdin = new Scanner(outputLine);
						cmdTemp = stdin.next();
						if (stdin.hasNext())
							stream_id = stdin.next();

						switch (cmdTemp) {
						case "STP":
							mesgOS.println("STP");
							running = false;
							break;
						case "SUB":
							if (streams.containsKey(stream_id) && stream_id != null) {
								String out = subscribe(stream_id);
								mesgOS.println("SUB " + out); // add ip
								System.out
										.println(ip + " subscribed to stream: "
												+ stream_id);
							} else {
								mesgOS.println("ERROR A stream by the name '"
										+ stream_id + "' doesn't exist");
								kkp.state = CommProtocol.WAITING;
							}
							break;
						case "UNS":
							unSubscribe();
							mesgOS.println("UNS");
							break;
						case "PUB":
							if (stream_id == null || streams.containsKey(stream_id)) {
								mesgOS.println("ERROR The name '"
										+ stream_id + "' is invalid");
								kkp.state = CommProtocol.WAITING;
							} else {
								publish(stream_id);
								mesgOS.println("PUB");
								System.out.println("Publishing:\n From:" + ip
										+ " stream id:" + stream_id);
							}

							break;
						case "UNP":
							unPublish();
							mesgOS.println("UNP");
							System.out.println("Unpublishing:\n From:" + ip
									+ "stream id:" + stream_id);
							break;
						case "FWD":
							forward(outputLine.getBytes());
							break;
						case "LST":
							String mesg;
							mesg = "LST ";
							for (String element : list())
								mesg = mesg + element + " ";
							mesgOS.println(mesg);
							break;
						default:
							mesgOS.println(outputLine);
						}
					}

				}
				System.out.println(ip + " closed connection");

			} catch (IOException e) {
				System.out.println("Error: processing the input");
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

		public String subscribe(String stream_id) {
			InetAddress tempStreamIps = streams.get(stream_id);
			recentStream = stream_id;
			streams.put(stream_id, tempStreamIps);
			return tempStreamIps.getHostAddress();
		}

		public void unSubscribe() {

		}

		public LinkedList<String> list() {
			LinkedList<String> keys = new LinkedList<String>();
			keys.addAll(streams.keySet());
			return keys;
		}

		public void publish(String stream_id) throws IOException {
			recentStream = stream_id;
			if (multScket.isClosed())
				multScket = new MulticastSocket(portNumber);
			InetAddress tempStreamIps = InetAddress
					.getByName(multicastIpAdressGroup);
			publishers.put(stream_id, clientSocket);
			streams.put(stream_id, tempStreamIps);
			try {
				multScket.joinGroup(tempStreamIps);
			} catch (IOException e) {
				System.out.println("Could not connect to ip multi cast group");
				e.printStackTrace();
			}
		}

		public void unPublish() throws IOException, InterruptedException {
			Socket tempStreamIp = publishers.get(recentStream);
			forward("STP".getBytes());
			if (tempStreamIp == clientSocket) {
				try {
					multScket.leaveGroup(streams.get(recentStream)); // Leave
																		// the
																		// group.
				} catch (IOException excpt) {
					System.err
							.println("Socket problem leaving group: " + excpt);
				}
				multScket.close(); // Close the socket.
				streams.remove(recentStream);
				publishers.remove(recentStream);
			}

		}

		public void forward(byte[] data) throws IOException {
			DatagramPacket dp = new DatagramPacket(data, data.length,
					InetAddress.getByName(multicastIpAdressGroup), portNumber);
			if (multScket != null)
				multScket.send(dp);
		}
	}
}
