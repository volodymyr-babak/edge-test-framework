package org.thingsboard.edgetest.solutions;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.thingsboard.edgetest.clients.Client;
import org.thingsboard.rest.client.RestClient;

public interface Solution {

    void install(RestClient restClient);
    void emulate(Client client, String hostname) throws MqttException;
}
