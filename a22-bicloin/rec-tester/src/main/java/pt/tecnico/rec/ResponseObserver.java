package pt.tecnico.rec;

import io.grpc.stub.StreamObserver;

public class ResponseObserver<R> implements StreamObserver<R> {
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);
    /** Helper method to print debug messages. */
    private static void debug(String debugMessage) {
        if (DEBUG_FLAG)
            System.err.print(debugMessage);
    }

    final ResponseCollector collector;
    final String ME = ResponseObserver.class.getSimpleName();
    Integer repnum;
    float weight;

    public ResponseObserver(ResponseCollector collector, Float weight, Integer repnum) {
        super();
        this.collector = collector;
        this.weight = weight;
        this.repnum = repnum;
    }

    @Override
    public void onNext(R r) {
        synchronized (collector) {
            try {
              collector.addResponse(r, weight, repnum);
            }
            catch (Exception e) {
                debug(ME + " caught exception in onNext(): " + e + "\n");
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        synchronized (collector){

            if(throwable.toString().contains("UNAVAILABLE")){
                collector.loseWeight(weight);
            }
            debug("Received error when contacting replica: " + throwable + "\n");
        }
    }

    @Override
    public void onCompleted() {
        //debug("Request completed\n");
    }
}
