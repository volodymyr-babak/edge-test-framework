package org.thingsboard.edgetest.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.thingsboard.edgetest.clients.Client;
import org.thingsboard.edgetest.data.common.TelemetryProfile;
import org.thingsboard.edgetest.data.emulation.EmulationDetails;
import org.thingsboard.rest.client.RestClient;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.TimePageLink;

import java.util.ArrayList;
import java.util.List;

public class DeviceEmulator extends Thread {

    private static final Logger logger = LogManager.getLogger(DeviceEmulator.class);

    private TelemetryProfile tp;

    private static AnnotationConfigApplicationContext context;

    private static RestClient restClientCloud;
    private static RestClient restClientEdge;
    private static String targetHostName;
    private static EmulationDetails emulationDetails;

    private List<String> deviceTelemetry;
    private List<String> cloudTelemetry;
    private List<String> edgeTelemetry;

    private Long startTs;
    private Long endTs;
    private int limit;

    public DeviceEmulator(TelemetryProfile tp) {
        super(tp.getDeviceDetails().getDeviceName() + " emulator");
        this.tp = tp;

        deviceTelemetry = new ArrayList<>();
        cloudTelemetry = new ArrayList<>();
        edgeTelemetry = new ArrayList<>();
    }

    public void run() {
        pushTelemetry();
        compareTelemetry();
    }

    private void pushTelemetry() {

        Client client = context.getBean(emulationDetails.getTelemetrySendProtocol(), Client.class);
        client.init(targetHostName, tp.getDeviceDetails().getAccessToken());
        logger.info("Starting push telemetry");

        limit = 0;
        startTs = System.currentTimeMillis();

        long breakTime = System.currentTimeMillis()+emulationDetails.getEmulationTime();

        while(System.currentTimeMillis()<breakTime) {
            String content = tp.generateContent();
            client.publish(content);
            deviceTelemetry.add(Converter.convertContentToSimpleString(content));
            limit++;
            try {
                Thread.sleep(tp.getPublishFrequencyInMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        client.disconnect();

        endTs = System.currentTimeMillis();

        logger.info("All telemetry pushed successfully");
    }

    private List<String> getTimeseries(RestClient restClient, String deviceName, List<String> keys) {
        TimePageLink timePageLink = new TimePageLink(limit, startTs, endTs);
        Device device = restClient.getTenantDevice(deviceName).get();
        List<TsKvEntry> timeseries = restClient.getTimeseries(device.getId(), keys, 0L, Aggregation.NONE, timePageLink);
        return Converter.convertTsKvEntryListToSimpleStringList(timeseries, keys.size());
    }

    private void compareTelemetry() {
        logger.info("Starting compare telemetry");
        cloudTelemetry = getTimeseries(restClientCloud, tp.getDeviceDetails().getDeviceName(), tp.getTelemetryKeys());
        edgeTelemetry = getTimeseries(restClientEdge, tp.getDeviceDetails().getDeviceName(), tp.getTelemetryKeys());
        logger.info(this.getName() + ": " + (deviceTelemetry.equals(cloudTelemetry) && cloudTelemetry.equals(edgeTelemetry) ? "all telemetry equals" : "telemetry not equals"));
    }

    static public void setEmulator(RestClient restClientCloud, RestClient restClientEdge, String targetHostName, EmulationDetails emulationDetails) {
        DeviceEmulator.restClientCloud = restClientCloud;
        DeviceEmulator.restClientEdge = restClientEdge;
        DeviceEmulator.targetHostName = targetHostName;
        DeviceEmulator.emulationDetails = emulationDetails;
        DeviceEmulator.context = new AnnotationConfigApplicationContext("org.thingsboard.edgetest.clients." + emulationDetails.getTelemetrySendProtocol());
    }

}
