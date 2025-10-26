package it.barboneantonello.oven;

import it.barboneantonello.oven.models.PhysicalPayloadMessage;
import it.barboneantonello.oven.utils.OvenDtShadowingFunction;
import it.wldt.adapter.http.digital.adapter.HttpDigitalAdapter;
import it.wldt.adapter.http.digital.adapter.HttpDigitalAdapterConfiguration;
import it.wldt.adapter.mqtt.digital.MqttDigitalAdapter;
import it.wldt.adapter.mqtt.digital.MqttDigitalAdapterConfiguration;
import it.wldt.adapter.mqtt.digital.topic.MqttQosLevel;
import it.wldt.adapter.mqtt.physical.MqttPhysicalAdapter;
import it.wldt.adapter.mqtt.physical.MqttPhysicalAdapterConfiguration;
import it.wldt.core.engine.DigitalTwin;
import it.wldt.core.engine.DigitalTwinEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DtOvenProcess {

    public static final Logger logger = LoggerFactory.getLogger(DtOvenProcess.class);


    public static void main(String[] args) {

        try {
            OvenDtShadowingFunction ovenDtShadowingFunction = new OvenDtShadowingFunction();
            DigitalTwin dt = new DigitalTwin(OvenDtShadowingFunction.getConfig().getDt_name(),
                                             ovenDtShadowingFunction);


            // -------------------- PHYSICAL ADAPTER ---------------------
            // MQTT
            String mqtt_physical_topic_prefix = OvenDtShadowingFunction.getConfig().getMqtt_adapters().getPhysical().getBase_topic();

            MqttPhysicalAdapterConfiguration mqttPhysicalAdapterConfiguration = MqttPhysicalAdapterConfiguration.builder(OvenDtShadowingFunction.getConfig().getMqtt_adapters().getPhysical().getBroker(),
                                                                                                                         OvenDtShadowingFunction.getConfig().getMqtt_adapters().getPhysical().getPort())
                    // Sensor
                    .addPhysicalAssetPropertyAndTopic("oven-door", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "door", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-carrier-clockwise", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "carrier/clockwise", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-carrier-counterclockwise", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "carrier/counterclockwise", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-heater", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "heater", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-input-photocell", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "input-photocell", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-carrier-inside", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "carrier-inside", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-carrier-outside", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "carrier-outside", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-temperature", new PhysicalPayloadMessage<>(0.0), mqtt_physical_topic_prefix + "temperature", DtOvenProcess::fromJsonToDoublePhysicalPayloadMessage)
              

                    // States
                    .addPhysicalAssetPropertyAndTopic("oven-state-busy", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "busy", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-state-working", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "working", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-state-anomaly", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "anomaly", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-state-resetting", new PhysicalPayloadMessage<>(false), mqtt_physical_topic_prefix + "resetting", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)
                    .addPhysicalAssetPropertyAndTopic("oven-state-available", new PhysicalPayloadMessage<>(true), mqtt_physical_topic_prefix + "available", DtOvenProcess::fromJsonToBooleanPhysicalPayloadMessage)

                    // Controls/Actions
                    .addPhysicalAssetActionAndTopic("controls", "Boolean", "text/plain", mqtt_physical_topic_prefix + "action", it.wldt.adapter.mqtt.physical.topic.MqttQosLevel.MQTT_QOS_0, false, String::valueOf)
                    .build();

            MqttPhysicalAdapter mqttPhysicalAdapter = new MqttPhysicalAdapter(OvenDtShadowingFunction.getConfig().getMqtt_adapters().getPhysical().getAdapter_id(),
                                                                              mqttPhysicalAdapterConfiguration);
            dt.addPhysicalAdapter(mqttPhysicalAdapter);

            // -----------------------------------------------------------




            // -------------------- DIGITAL ADAPTER ----------------------
            // HTTP
            HttpDigitalAdapterConfiguration http_digital_config = new HttpDigitalAdapterConfiguration(OvenDtShadowingFunction.getConfig().getMqtt_adapters().getPhysical().getAdapter_id(),
                                                                                                      OvenDtShadowingFunction.getConfig().getHttp_digital_adapter().getHost(),
                                                                                                      OvenDtShadowingFunction.getConfig().getHttp_digital_adapter().getPort());
            dt.addDigitalAdapter(new HttpDigitalAdapter(http_digital_config, dt));


            // MQTT
            String mqtt_digital_topic_prefix = OvenDtShadowingFunction.getConfig().getMqtt_adapters().getDigital().getBase_topic();

            MqttDigitalAdapterConfiguration mqttDigitalAdapterConfiguration = MqttDigitalAdapterConfiguration.builder(OvenDtShadowingFunction.getConfig().getMqtt_adapters().getDigital().getBroker(),
                                                                                                                      OvenDtShadowingFunction.getConfig().getMqtt_adapters().getDigital().getPort())
                    .addPropertyTopic("machine-state", mqtt_digital_topic_prefix + "state", MqttQosLevel.MQTT_QOS_0, false, String::valueOf)
                    .addPropertyTopic("OEE", mqtt_digital_topic_prefix + "OEE", MqttQosLevel.MQTT_QOS_0, false, String::valueOf)
                    // Controls/Actions
                    .addActionTopic("controls", mqtt_digital_topic_prefix + "action", String::valueOf)
                    .addActionTopic("start-oee", mqtt_digital_topic_prefix + "start-oee-computation", String::valueOf)
                    .build();

            MqttDigitalAdapter mqttDigitalAdapter = new MqttDigitalAdapter(OvenDtShadowingFunction.getConfig().getMqtt_adapters().getDigital().getAdapter_id(),
                                                                           mqttDigitalAdapterConfiguration);
            dt.addDigitalAdapter(mqttDigitalAdapter);

            // -----------------------------------------------------------

            // Create the Digital Twin Engine
            DigitalTwinEngine digitalTwinEngine = new DigitalTwinEngine();

            // Add the Digital Twin to the Engine
            digitalTwinEngine.addDigitalTwin(dt);

            /*
            // Set a new Event-Logger to a Custom One that we created with the class 'DemoEventLogger'
            WldtEventBus.getInstance().setEventLogger(new DemoEventLogger());
            */

            // Start all the DTs registered on the engine
            digitalTwinEngine.startAll();

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private static PhysicalPayloadMessage<Boolean> fromJsonToBooleanPhysicalPayloadMessage(String json) {
        return PhysicalPayloadMessage.fromJson(json, Boolean.class);
    }

    private static PhysicalPayloadMessage<Double> fromJsonToDoublePhysicalPayloadMessage(String json) {
        return PhysicalPayloadMessage.fromJson(json, Double.class);
    }
}
