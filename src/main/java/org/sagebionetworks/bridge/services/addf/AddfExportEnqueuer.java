package org.sagebionetworks.bridge.services.addf;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.worker.AddfExportRequest;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;

/**
 * Enqueues a record for the ADDF export pipeline. This runs as an independent sibling of the Exporter 3.0 send in
 * {@link org.sagebionetworks.bridge.services.Exporter3Service}: it publishes a separate message to the ADDF request
 * queue and is gated by the {@code addf.export.enabled} kill-switch.
 *
 * <p>All failures are logged and swallowed. An ADDF enqueue failure must never fail {@code completeUpload} or interfere
 * with the Exporter 3.0 send, which stays first and unguarded exactly as before.</p>
 */
@Component
public class AddfExportEnqueuer {
    private static final Logger LOG = LoggerFactory.getLogger(AddfExportEnqueuer.class);

    static final String WORKER_NAME_ADDF_EXPORT = "AddfExportWorker";

    private BridgeConfig config;
    private AmazonSQS sqsClient;

    @Autowired
    public final void setConfig(BridgeConfig config) {
        this.config = config;
    }

    @Autowired
    public final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    /** Enqueue an ADDF export request for the given record. No-op when the kill-switch is off. */
    public void enqueue(String appId, String recordId) {
        if (!Boolean.parseBoolean(config.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED))) {
            return;
        }

        try {
            AddfExportRequest addfRequest = new AddfExportRequest();
            addfRequest.setAppId(appId);
            addfRequest.setRecordId(recordId);

            WorkerRequest workerRequest = new WorkerRequest();
            workerRequest.setService(WORKER_NAME_ADDF_EXPORT);
            workerRequest.setBody(addfRequest);

            ObjectMapper objectMapper = BridgeObjectMapper.get();
            String requestJson = objectMapper.writeValueAsString(workerRequest);

            // Note: SqsInitializer runs after Spring, so we need to grab the queue URL dynamically.
            String addfQueueUrl = config.getProperty(BridgeConstants.CONFIG_KEY_ADDF_SQS_URL);

            SendMessageResult sqsResult = sqsClient.sendMessage(addfQueueUrl, requestJson);
            LOG.info("Sent ADDF export request for app " + appId + " record " + recordId + "; received message ID=" +
                    sqsResult.getMessageId());
        } catch (Exception ex) {
            // Log and swallow: ADDF is a best-effort sibling of Exporter 3.0 and must never break the upload path.
            LOG.error("Failed to enqueue ADDF export request for app " + appId + " record " + recordId + ": " +
                    ex.getMessage(), ex);
        }
    }
}
