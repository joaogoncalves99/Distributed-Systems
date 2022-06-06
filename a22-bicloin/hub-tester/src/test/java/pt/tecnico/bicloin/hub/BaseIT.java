package pt.tecnico.bicloin.hub;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.*;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;


public class BaseIT {

	private static final String TEST_PROP_FILE = "/test.properties";
	protected static Properties testProps;
	protected static HubFrontend frontend;

	@BeforeAll
	public static void oneTimeSetup () throws IOException {
		testProps = new Properties();

		try {
			testProps.load(BaseIT.class.getResourceAsStream(TEST_PROP_FILE));
			System.out.println("Test properties:");
			System.out.println(testProps);

			frontend = new HubFrontend(testProps.getProperty("zoo.host"), testProps.getProperty("zoo.port"), testProps.getProperty("hub.path"));
		}catch (IOException e) {
			final String msg = String.format("Could not load properties file {}", TEST_PROP_FILE);
			System.out.println(msg);
			throw e;
		} catch (ZKNamingException zke) {
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		}
	}
	@AfterAll
	public static void cleanup() {
		frontend.shutdown();
	}

}
