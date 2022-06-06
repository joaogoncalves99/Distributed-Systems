package pt.tecnico.rec;

import java.util.List;
import java.util.ArrayList;

public class ResponseCollector {

    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);
    /** Helper method to print debug messages. */
    private static void debug(String debugMessage) {
        if (DEBUG_FLAG)
            System.err.print(debugMessage);
    }

    volatile float expectedResponseAmount = 0;
    volatile float responseCounter = 0;
    List<Object> responseCollection = new ArrayList<>();

    volatile float lostWeight = 0;
    volatile boolean notificationStatus = false;

    public List<Object> getResponseList() {
        synchronized (this) {
            return this.responseCollection;
        }
    }

    public float getLostWeight() {
        synchronized (this) {
            return this.lostWeight;
        }
    }

    public boolean getNotificationStatus() {
        synchronized (this) {
            return this.notificationStatus;
        }
    }

    public void setExpectedResponseAmount(float expectedResponseAmount){
        synchronized (this) {
            this.expectedResponseAmount = expectedResponseAmount;
        }
    }

    public void loseWeight(float weight) {
        synchronized (this){
            lostWeight += weight;
        }
    }

    public void clearResponseList() {
      this.responseCollection.clear();
      this.responseCounter = 0;
      this.expectedResponseAmount = 0;
    }

    public <R> void addResponse(R r, float weight, Integer repnum) {
      debug("Received read from replica " + repnum + " with weight " + weight + "\n");
      if(responseCounter < expectedResponseAmount){
        responseCollection.add(r);
        responseCounter += weight;
        if(responseCounter >= expectedResponseAmount) {
            this.notificationStatus = true;
            this.notifyAll();
        }
      }

    }
}
