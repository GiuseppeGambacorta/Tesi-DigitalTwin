package io.github.wldt.demo;

import java.util.ArrayList;
import java.util.function.Function;

import io.github.wldt.demo.digital.DemoConfDigitalAdapter;
import io.github.wldt.demo.digital.DemoDigitalAdapterConfiguration;
import io.github.wldt.demo.logger.DemoEventLogger;
import io.github.wldt.demo.physical.DemoConfPhysicalAdapter;
import io.github.wldt.demo.physical.DemoPhysicalAdapterConfiguration;
import it.wldt.adapter.mqtt.physical.MqttPhysicalAdapter;
import it.wldt.adapter.mqtt.physical.MqttPhysicalAdapterConfiguration;
import it.wldt.adapter.mqtt.physical.topic.incoming.DigitalTwinIncomingTopic;
import it.wldt.core.engine.DigitalTwin;
import it.wldt.core.engine.DigitalTwinEngine;
import it.wldt.core.event.WldtEventBus;

/**
 * Main class to build and test a demo Digital Twin with the created physical and digital adapters
 *
 * @author Marco Picone, Ph.D. (picone.m@gmail.com)
 */
public class DemoDigitalTwin {

    public static void main(String[] args)  {
        try{

            // Create the new Digital Twin
            DigitalTwin digitalTwin = new DigitalTwin(
                    "mqtt-digital-twin",
                    new DemoShadowingFunction("test-shadowing-function")
            );

            

            MqttPhysicalAdapterConfiguration config = MqttPhysicalAdapterConfiguration.builder("127.0.0.1", 1883)
            .addPhysicalAssetPropertyAndTopic("intensity", 0, "Palletizzatore/MainMachine/Conteggio", Integer::parseInt)
           // .addIncomingTopic(new DigitalTwinIncomingTopic("sensor/state", getSensorStateFunction()), createIncomingTopicRelatedPropertyList(), new ArrayList<>())
           // .addPhysicalAssetEventAndTopic("overheating", "text/plain", "sensor/overheating", Function.identity())
           // .addPhysicalAssetActionAndTopic("switch-off", "sensor.actuation", "text/plain", "sensor/actions/switch", actionBody -> "switch" + actionBody)
            .build();

            MqttPhysicalAdapter mqttPhysicalAdapter = new MqttPhysicalAdapter("test-mqtt-pa", config);
            digitalTwin.addPhysicalAdapter(mqttPhysicalAdapter);

            //Default Physical and Digital Adapter
            //digitalTwin.addPhysicalAdapter(new DemoPhysicalAdapter("test-physical-adapter"));
            //digitalTwin.addDigitalAdapter(new DemoDigitalAdapter("test-digital-adapter"));

            //Physical and Digital Adapters with Configuration
            //digitalTwin.addPhysicalAdapter(new DemoConfPhysicalAdapter("test-physical-adapter", new DemoPhysicalAdapterConfiguration()));
            //digitalTwin.addDigitalAdapter(new DemoConfDigitalAdapter("test-digital-adapter", new DemoDigitalAdapterConfiguration()));

            // Create the Digital Twin Engine
            DigitalTwinEngine digitalTwinEngine = new DigitalTwinEngine();

            // Add the Digital Twin to the Engine
            digitalTwinEngine.addDigitalTwin(digitalTwin);

            // Set a new Event-Logger to a Custom One that we created with the class 'DemoEventLogger'
            WldtEventBus.getInstance().setEventLogger(new DemoEventLogger());

            // Start all the DTs registered on the engine
            digitalTwinEngine.startAll();

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
