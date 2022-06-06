package pt.tecnico.bicloin.hub;

import java.io.IOException;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;

public class HubMain {
	private static final String hubServerPath = "/grpc/bicloin/hub/";

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println(HubMain.class.getSimpleName());

		// receive and print arguments
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// check arguments
		if (args.length < 7) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s <ZooKeeperHost> <ZooKeeperPort> <ServerHost> <ServerPort>", HubMain.class.getName());
			return;
		}

		String zkHost = args[0];
		String zkPort = args[1];
		String serverHost = args[2];
		String serverPort = args[3];
		String serverPathSufix = args[4];

		String dataFilePath1 = args[5];
		String dataFilePath2 = args[6];

		boolean innitRec = (args[7] != null && args[7].equals("initRec"));

		ZKNaming zkNaming = null;
		try {
			zkNaming = new ZKNaming(zkHost, zkPort);
			zkNaming.rebind(hubServerPath + serverPathSufix, serverHost, serverPort);
		}
		catch(ZKNamingException zke) {
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		}

		try {
			final int port = Integer.parseInt(serverPort);
			HubServerImpl serverImpl = new HubServerImpl(zkHost, zkPort, dataFilePath1, dataFilePath2, innitRec);
			final BindableService impl = serverImpl;

			Server hubServer = ServerBuilder.forPort(port).addService(impl).build();

			hubServer.start();

			// Server threads are running in the background.
			System.out.println("Server started");

			hubServer.awaitTermination();
			serverImpl.ShutdownRecConnection();


			if (zkNaming != null) {
				// remove
				zkNaming.unbind(hubServerPath + serverPathSufix, serverHost, serverPort); //Arg4 = server Path, Arg2 = rec IP, Arg3 = rec Port
			}
		} catch (ZKNamingException zke) {
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		} catch (IllegalArgumentException iae) {
			System.out.println("Initialization of data failed.");
		}
	}

}
