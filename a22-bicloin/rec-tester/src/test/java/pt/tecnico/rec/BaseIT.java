package pt.tecnico.rec;

import java.io.IOException;
import java.util.Properties;

import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;

import org.junit.jupiter.api.*;

public class BaseIT {

	private static final String TEST_PROP_FILE = "/test.properties";
	protected static Properties testProps;
	protected static RecordFrontend frontend;

	@BeforeAll
	public static void oneTimeSetup () throws IOException {
		testProps = new Properties();

		try {
			testProps.load(BaseIT.class.getResourceAsStream(TEST_PROP_FILE));
			System.out.println("Test properties:");
			System.out.println(testProps);

			frontend = new RecordFrontend(testProps.getProperty("zoo.host"), testProps.getProperty("zoo.port"), testProps.getProperty("rec.path"));
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
	public static void oneTimeTearDown() {
		if(frontend != null)
			frontend.shutdown();
	}

}
