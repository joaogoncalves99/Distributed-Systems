package pt.tecnico.rec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import io.grpc.StatusRuntimeException;
import pt.tecnico.rec.grpc.Rec.PingRequest;
import pt.tecnico.rec.grpc.Rec.PingResponse;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;

import static io.grpc.Status.INVALID_ARGUMENT;

public class PingIT extends BaseIT{

    @Test
	public void pingOKTest() {
		PingRequest request = PingRequest.newBuilder().setInput("friend").build();
		PingResponse response = frontend.ping(request);
		assertEquals("Hello friend!", response.getOutput());
	}

    @Test
    public void emptyPingTest() {
		PingRequest request = PingRequest.newBuilder().setInput("").build();
		assertEquals(
		INVALID_ARGUMENT.getCode(),
		assertThrows(
		StatusRuntimeException.class, () -> frontend.ping(request))
		.getStatus()
		.getCode());
	}
}
