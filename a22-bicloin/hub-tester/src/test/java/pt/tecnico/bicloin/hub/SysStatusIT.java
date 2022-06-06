package pt.tecnico.bicloin.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pt.tecnico.bicloin.hub.grpc.Hub.SystemStatusRequest;
import pt.tecnico.bicloin.hub.grpc.Hub.SystemStatusResponse;

import java.util.List;
import java.util.stream.Collectors;

public class SysStatusIT extends BaseIT {
	static String hubPath = "/grpc/bicloin/hub/";
	static String recPath = "/grpc/bicloin/rec/";

    @Test
	public void systemStatusTest() {
		SystemStatusRequest request = SystemStatusRequest.newBuilder().setInput("friend").build();
		SystemStatusResponse response = frontend.sysStatus(request);

		List<String> hubPaths = response.getServerPathList().stream()
				.filter(x -> x.startsWith(hubPath)).collect(Collectors.toList());
		List<String> recPaths = response.getServerPathList().stream()
				.filter(x -> x.startsWith(recPath)).collect(Collectors.toList());

		int i = 1;
		for (String path : hubPaths) {
			assertEquals(hubPath + i, path);
			i++;
		}
		i = 1;
		for (String path : recPaths) {
			assertEquals(recPath + i, path);
			i++;
		}
	}
}
