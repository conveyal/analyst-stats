package com.conveyal.analyst.stats;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentripplanner.analyst.cluster.TaskStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Listen for statistics.
 */
public class StatsListener implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(StatsListener.class);

    private static final AmazonSQSClient client = new AmazonSQSClient();

    static {
        Region region = Regions.getCurrentRegion();

        if (region != null)
            client.setRegion(region);
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override public void run() {


        // get the queue
        String queueUrl = client.getQueueUrl(StatsMain.config.getProperty("application.stats-queue")).getQueueUrl();

        GLOBAL: while (true) {
            try {
                // long-poll for stats
                ReceiveMessageRequest rmq = new ReceiveMessageRequest();
                rmq.setMaxNumberOfMessages(10);
                rmq.setQueueUrl(queueUrl);
                rmq.setWaitTimeSeconds(20);
                ReceiveMessageResult res = client.receiveMessage(rmq);

                List<DeleteMessageBatchRequestEntry> entries = new ArrayList<>();

                for (Message m : res.getMessages()) {
                    // store the message
                    long count = StatsMain.store.store(mapper.readValue(m.getBody(), TaskStatistics.class));

                    DeleteMessageBatchRequestEntry e = new DeleteMessageBatchRequestEntry();
                    e.setId(m.getMessageId());
                    e.setReceiptHandle(m.getReceiptHandle());
                    entries.add(e);

                    if (count % 100 == 0)
                        LOG.info("Stored {} stats", count);
                }

                if (!entries.isEmpty()) {
                    DeleteMessageBatchRequest dmbr = new DeleteMessageBatchRequest();
                    dmbr.setQueueUrl(queueUrl);
                    dmbr.setEntries(entries);
                    client.deleteMessageBatch(dmbr);
                }
            } catch (Exception e) {
                LOG.info("error retrieving stats", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    break GLOBAL;
                }
            }
        }
    }
}
