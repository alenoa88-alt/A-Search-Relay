package com.asearch.relay;

public final class FutureWorkers {
    private FutureWorkers() {}

    public interface DataWorker {
        String sourceName();
        WorkerFacts gatherFacts(long sinceTimestamp);
    }

    public interface BeeperWorker extends DataWorker {}
    public interface GmailWorker extends DataWorker {}
    public interface CalendarWorker extends DataWorker {}
    public interface ResearchWorker extends DataWorker {}

    public static final class WorkerFacts {
        public String source;
        public long gatheredAt;
        public String checkpoint;
        public String factsJson;
    }
}
