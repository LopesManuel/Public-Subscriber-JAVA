import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class Client {

	public final static int BUFFER_SIZE = 4024;
	static boolean publishing = false;
	static boolean waitingForAcess = false;

	public static void main(String args[]) throws Exception {
		int portNumber = 4444;
		int bytesRead;

		String hostName = args[0];
		byte[] fromServer, fromUser;
		String userInput;

		Scanner stdIn = new Scanner(System.in);
		Socket MyClient;
		PrintWriter msgOut = null;

		try {
			MyClient = new Socket(hostName, portNumber);
			OutputStream out = MyClient.getOutputStream();

			msgOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
					MyClient.getOutputStream())), true);
			InputThread inputFromServer = new InputThread(MyClient, msgOut);
			inputFromServer.start();
			Thread.sleep(500);
			System.out.print(">");

			fromUser = new byte[BUFFER_SIZE];
			while (stdIn.hasNextLine()) {
				userInput = stdIn.nextLine();
				if (fromUser != null && userInput.length() > 0) {
					Scanner lineScanner = new Scanner(userInput);
					String next = lineScanner.next();
					if (waitingForAcess) {
						System.out.println("Waiting for server...");
					}
					if (next.equals("publish") && !publishing) {
						msgOut.println(userInput);
						waitingForAcess = true;
					} else if (next.equals("unpublish") && publishing) {
						publishing = false;
						msgOut.println(userInput);
					} else if (next.equals("subscribe") && !publishing
							&& !inputFromServer.subbed) {
						inputFromServer.subbed = true;
						waitingForAcess = true;
						msgOut.println(userInput);
					} else if (next.equals("unsubscribe" )
							&& inputFromServer.subbed) {
						inputFromServer.subbed = false;
						inputFromServer.s.leaveGroup(inputFromServer.groupIp);
						msgOut.println(userInput);
					} else if (!publishing)
						msgOut.println(userInput);
				}
				Thread.sleep(250);
				System.out.print(">");
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			System.out.println("Server might be down, try again later!");
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Server might be down, try again later!");
			e.printStackTrace();
		}

	}

	public static class PublishThread extends Thread {
		int bytesRead;
		private PrintWriter out;
		boolean running = false;
		boolean alive = true;

		public PublishThread(Socket MyClient) throws Exception {
			this.out = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(MyClient.getOutputStream())), true);
		}

		public void run() {
			while (alive) {
				while (running) {

					Random generator = new Random();
					double streamData = generator.nextDouble() * 100.0;
					out.println(("" + streamData));
					try {
						sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						running = false;
					}
				}
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		public byte[] toByteArray(double value) {
			byte[] bytes = new byte[8];
			ByteBuffer.wrap(bytes).putDouble(value);
			return bytes;
		}

		public double toDouble(byte[] bytes) {
			return ByteBuffer.wrap(bytes).getDouble();
		}

		public synchronized void pause() {
			running = false;
		}

		public synchronized void unpause() {
			running = true;
		}
	}

	public static class InputThread extends Thread {
		int bytesRead;
		private InputStream in;
		private BufferedReader inBuff;
		public boolean subbed = false;
		public InetAddress groupIp = null;
		public MulticastSocket s;
		private byte[] fromServer = new byte[BUFFER_SIZE];
		private DatagramPacket dp = new DatagramPacket(fromServer,
				fromServer.length);
		private PublishThread pb = null;
		private PrintWriter print = null;

		public InputThread(Socket MyClient, PrintWriter print) throws Exception {
			this.in = MyClient.getInputStream();
			this.inBuff = new BufferedReader(new InputStreamReader(
					MyClient.getInputStream()));
			this.s = new MulticastSocket(4444);
			this.pb = new PublishThread(MyClient);
			this.print = print;
		}

		public void run() {
			pb.start();
			Scanner nstr;
			String ip = null;
			String cmd = null;
			try {
				while (!this.isInterrupted()) {
					if (!subbed) {
						String str = inBuff.readLine();
						nstr = new Scanner(str);
						if (nstr.hasNext()) {
							cmd = nstr.next();
							if (nstr.hasNext())
								ip = nstr.next();
							if (cmd.equals("STP")) {
								System.out.println("Good Bye!");
								System.exit(0);
								break;
							} else if (cmd.matches("SUB") && ip != null) {
								waitingForAcess = false;
								groupIp = InetAddress.getByName(ip);
								s.joinGroup(groupIp);
								subbed = true;
								str = "Sucessfuly subscribed";
							} else if (cmd.matches("UNS") && ip != null) {
								waitingForAcess = false;
								s.leaveGroup(groupIp);
								subbed = false;
								str = "Sucessfuly unsubscribed";
							} else if (cmd.matches("PUB")) {
								publishing = true;
								waitingForAcess = false;
								pb.unpause();
								str = "You are now streaming";
							} else if (cmd.matches("UNP")) {
								publishing = false;
								waitingForAcess = false;
								pb.pause();
								str = "Sucessfuly unpublished";
							} else if (cmd.matches("LST")) {
								str = "\nSTREAMS AVAILABLE:\n";
								if (ip != null)
									str = str + "\t" + ip + "\n";
								while (nstr.hasNext()) {
									str = "\t" + nstr.next() + "\n";
								}
							} else if (cmd.matches("ERROR")) {
								waitingForAcess = false;
								publishing = false;
								subbed = false;
							}
						}
						System.out.println("Server: " + str);
						ip = null;
						cmd = null;
					} else {
						s.receive(dp);
						String str = new String(dp.getData());
						Scanner lineScanner = new Scanner(str);
						String next = lineScanner.next();
						String msg = null;
						if (lineScanner.hasNext())
							msg = lineScanner.next();
						if (next.equals("STP")) {
							msg = "This stream has stopped!";
							subbed = false;
							print.println("unsubscribe");
						}
						System.out.println("Server: " + msg);
					}
					try {
						sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} catch (IOException e) {
				System.out.println("Can't get socket input stream. ");
			}
		}
	}
}