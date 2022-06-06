package pt.tecnico.bicloin.hub;

import com.google.type.Money;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bicloin.hub.grpc.Hub;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;

public class HubTester {

	public static void main(String[] args) {
		System.out.println(HubTester.class.getSimpleName());

		// receive and print arguments
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// check arguments
		if (args.length < 2) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s host port%n", HubTester.class.getName());
			return;
		}

		final String host = args[0];
		final String port = args[1];
		final String path = args[4];

		HubFrontend frontend = null;
		try {
			frontend = new HubFrontend(host, port, path);
		} catch (StatusRuntimeException e) {
			System.out.println("Caught exception with description: " +
			e.getStatus().getDescription());
		} catch (ZKNamingException zke){
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		}
		if(frontend != null)
			frontend.shutdown();
	}

}
