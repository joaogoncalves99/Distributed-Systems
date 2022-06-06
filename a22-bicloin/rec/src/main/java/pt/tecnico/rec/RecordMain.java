package pt.tecnico.rec;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;

import java.io.IOException;

public class RecordMain {
	private static final String recServerPath = "/grpc/bicloin/rec/";

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println(RecordMain.class.getSimpleName());
		ZKNaming zkNaming = null;

		// receive and print arguments
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// check arguments
		if (args.length < 5) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s <ZooKeeperHost> <ZooKeeperPort> <ServerHost> <ServerPort>", RecordMain.class.getName());
			return;
		}
		String zooHost = args[0];
		String zooPort = args[1];
		String serverHost = args[2];
		String serverPort = args[3];
		String serverPathSufix = args[4];

		try {
			zkNaming = new ZKNaming(zooHost, zooPort);
			zkNaming.rebind(recServerPath + serverPathSufix, serverHost, serverPort);
		}
		catch(ZKNamingException zke) {
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		}

		final int port = Integer.parseInt(args[3]);
		final BindableService impl = new RecordServerImpl();

		Server recServer = ServerBuilder.forPort(port).addService(impl).build();

		recServer.start();

		// Server threads are running in the background.
		System.out.println("Replica " + args[4] + " started");

		recServer.awaitTermination();

		try {
			if (zkNaming != null) {
				// remove
				zkNaming.unbind(recServerPath + serverPathSufix, serverHost, serverPort); //Arg4 = server Path, Arg2 = rec IP, Arg3 = rec Port
			}
		} catch (ZKNamingException zke) {
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		}
	}

}
