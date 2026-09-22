package org.sagebionetworks.bridge.services.addf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.worker.AddfExportRequest;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;

/**
 * The ADDF upload fan-out is a best-effort sibling of the Exporter 3.0 send: it must be silent when the kill-switch is
 * off and it must never let a failure escape into {@code completeUpload}. Both are invariants of the Phase 2 design and
 * neither was asserted anywhere before this test.
 */
public class AddfExportEnqueuerTest {
    private static final String ADDF_QUEUE_URL = "http://example.com/addf-queue";
    private static final String RECORD_ID = "test-record";

    private BridgeConfig mockConfig;
    private AmazonSQS mockSqsClient;
    private AddfExportEnqueuer enqueuer;

    @BeforeMethod
    public void before() {
        mockConfig = mock(BridgeConfig.class);
        mockSqsClient = mock(AmazonSQS.class);
        when(mockConfig.getProperty(BridgeConstants.CONFIG_KEY_ADDF_SQS_URL)).thenReturn(ADDF_QUEUE_URL);
        // Return type only matters for the log line, but it must not be null or the NPE would be swallowed and every
        // test would pass for the wrong reason.
        when(mockSqsClient.sendMessage(anyString(), anyString())).thenReturn(new SendMessageResult());

        enqueuer = new AddfExportEnqueuer();
        enqueuer.setConfig(mockConfig);
        enqueuer.setSqsClient(mockSqsClient);
    }

    @Test
    public void enabledSendsAddfWorkerRequest() throws Exception {
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn("true");

        enqueuer.enqueue(TestConstants.TEST_APP_ID, RECORD_ID);

        ArgumentCaptor<String> requestJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSqsClient).sendMessage(eq(ADDF_QUEUE_URL), requestJsonCaptor.capture());

        WorkerRequest workerRequest = BridgeObjectMapper.get().readValue(requestJsonCaptor.getValue(),
                WorkerRequest.class);
        assertEquals(workerRequest.getService(), AddfExportEnqueuer.WORKER_NAME_ADDF_EXPORT);

        // WorkerRequest.body carries no inherent typing information, so convert it again (same idiom as
        // Exporter3ServiceTest).
        AddfExportRequest addfRequest = BridgeObjectMapper.get().convertValue(workerRequest.getBody(),
                AddfExportRequest.class);
        assertEquals(addfRequest.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(addfRequest.getRecordId(), RECORD_ID);
    }

    @Test
    public void killSwitchOffSendsNothing() {
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn("false");

        enqueuer.enqueue(TestConstants.TEST_APP_ID, RECORD_ID);

        verify(mockSqsClient, never()).sendMessage(anyString(), anyString());
        // The gate returns before the queue URL is even resolved — nothing about ADDF is touched.
        verify(mockConfig, never()).getProperty(BridgeConstants.CONFIG_KEY_ADDF_SQS_URL);
    }

    @Test
    public void absentKillSwitchSendsNothing() {
        // An unset key parses as false: ADDF stays off unless an environment explicitly turns it on.
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn(null);

        enqueuer.enqueue(TestConstants.TEST_APP_ID, RECORD_ID);

        verify(mockSqsClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    public void sqsFailureIsLoggedAndSwallowed() {
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn("true");
        doThrow(new AmazonServiceException("SQS is down")).when(mockSqsClient).sendMessage(anyString(), anyString());

        // Must return normally. If this throws, an ADDF outage takes down completeUpload with it.
        enqueuer.enqueue(TestConstants.TEST_APP_ID, RECORD_ID);

        verify(mockSqsClient).sendMessage(eq(ADDF_QUEUE_URL), any(String.class));
    }

    @Test
    public void unresolvedQueueUrlIsSwallowed() {
        // SqsInitializer not having resolved the .url yet: sendMessage(null, ...) fails inside the try, not outside it.
        when(mockConfig.get(BridgeConstants.CONFIG_KEY_ADDF_ENABLED)).thenReturn("true");
        when(mockConfig.getProperty(BridgeConstants.CONFIG_KEY_ADDF_SQS_URL)).thenReturn(null);
        doThrow(new IllegalArgumentException("queue URL is null")).when(mockSqsClient)
                .sendMessage(eq((String) null), anyString());

        enqueuer.enqueue(TestConstants.TEST_APP_ID, RECORD_ID);
    }
}
