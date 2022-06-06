package pt.tecnico.bicloin.app;

import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;

import java.util.NoSuchElementException;
import java.util.Scanner;

public class AppMain {
	static private String zkHubServerPaths = "/grpc/bicloin/hub";

	public static void main(String[] args) {
		System.out.println(AppMain.class.getSimpleName());

		// receive and print arguments
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		String zooKeeperHost = args[0];
		String zooKeeperPort = args[1];
		String userID = args[2];
		String userPhoneNumber = args[3];
		double userLatitude = Double.parseDouble(args[4]);
		double userLongitude = Double.parseDouble(args[5]);

		App app = new App();

		try {
			if(!app.ConnectToServer(zooKeeperHost, zooKeeperPort, zkHubServerPaths)) {
				System.out.println("ERRO Aplicação não conseguiu aceder o servidor");
				app.Shutdown();
				return;
			}
			app.LoginToServer(userID, userPhoneNumber, userLatitude, userLongitude);

			Scanner inputScanner = new Scanner(System.in);
			do{
				System.out.print("> ");
			}while (app.ExecuteCommand(inputScanner.nextLine()));

			app.Shutdown();
		}
		catch (ZKNamingException zke) {
			app.Shutdown();
			System.out.println("ERRO Aplicação não conseguiu encontrar/aceder o servidor");
		}
		catch (NoSuchElementException nse) {
			app.Shutdown();
			System.out.println("OK Fim de ficheiro");
		}
		catch (IllegalArgumentException iae) {
			app.Shutdown();
			System.out.println("ERRO Login de utilizador errado");
		}
		catch (Exception e) {
			app.Shutdown();
			System.out.println("ERRO Ocorreu um problema inesperado");
			app.debug(e.getMessage());
		}
	}

}
