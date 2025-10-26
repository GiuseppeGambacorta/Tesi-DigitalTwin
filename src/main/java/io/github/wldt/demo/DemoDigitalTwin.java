package io.github.wldt.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.Gson;

import io.github.wldt.demo.DemoDigitalTwin.SingleValueDescriptor;
import io.github.wldt.demo.digital.DemoConfDigitalAdapter;
import io.github.wldt.demo.digital.DemoDigitalAdapterConfiguration;
import io.github.wldt.demo.logger.DemoEventLogger;
import io.github.wldt.demo.physical.DemoConfPhysicalAdapter;
import io.github.wldt.demo.physical.DemoPhysicalAdapterConfiguration;
import it.wldt.adapter.mqtt.physical.MqttPhysicalAdapter;
import it.wldt.adapter.mqtt.physical.MqttPhysicalAdapterConfiguration;
import it.wldt.adapter.mqtt.physical.topic.incoming.DigitalTwinIncomingTopic;
import it.wldt.adapter.mqtt.physical.topic.incoming.MqttSubscribeFunction;
import it.wldt.adapter.physical.PhysicalAssetEvent;
import it.wldt.adapter.physical.PhysicalAssetProperty;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.core.engine.DigitalTwin;
import it.wldt.core.engine.DigitalTwinEngine;
import it.wldt.core.event.WldtEvent;
import it.wldt.core.event.WldtEventBus;
import it.wldt.exception.EventBusException;





/**
 * Main class to build and test a demo Digital Twin with the created physical and digital adapters
 *
 * @author Marco Picone, Ph.D. (picone.m@gmail.com)
 */
public class DemoDigitalTwin {


    public static class SingleValueDescriptor {
        private String Value;
        public String getValue() { return Value; }
    }


    private static MqttSubscribeFunction MapSingleValue(String topic){
        return msgPayload -> {
            SingleValueDescriptor buttonStatus = new Gson().fromJson(msgPayload, SingleValueDescriptor.class);
            List<WldtEvent<?>> events = new ArrayList<>();
            try {
                events.add(new PhysicalAssetPropertyWldtEvent<>(topic+"/Value", buttonStatus.getValue()));
            } catch (EventBusException e) {
                e.printStackTrace();
            }
            return events;
        };
    }

    private static List<PhysicalAssetProperty<?>> CreateSingleValueProperties(String topic) {
            List<PhysicalAssetProperty<?>> properties = new ArrayList<>();
            
            properties.add(new PhysicalAssetProperty<>(topic+"/Value", ""));
            return properties;
        }

    public static void main(String[] args)  {
        try{

            // Create the new Digital Twin
            DigitalTwin machineForMesTwin = new DigitalTwin(
                    "machineforMesTwin",
                    new DemoShadowingFunction("test-shadowing-function")
            );

            String tempTopic = "PickAndPlace/MainMachine/";

            MqttPhysicalAdapterConfiguration config = MqttPhysicalAdapterConfiguration.builder("127.0.0.1", 1883)
         
         
            .addIncomingTopic(
                new DigitalTwinIncomingTopic(tempTopic + "MachineMode", MapSingleValue(tempTopic + "MachineMode")), CreateSingleValueProperties(tempTopic + "MachineMode"), new ArrayList<>()
            )
            .addIncomingTopic(
                new DigitalTwinIncomingTopic(tempTopic + "ConteggioCicli", MapSingleValue(tempTopic + "ConteggioCicli")), CreateSingleValueProperties(tempTopic + "ConteggioCicli"), new ArrayList<>()
            )
          
            .build();

          
            MqttPhysicalAdapter mqttPhysicalAdapter = new MqttPhysicalAdapter("test-mqtt-pa", config);
            machineForMesTwin.addPhysicalAdapter(mqttPhysicalAdapter);

            //Default Physical and Digital Adapter
            //digitalTwin.addPhysicalAdapter(new DemoPhysicalAdapter("test-physical-adapter"));
            //digitalTwin.addDigitalAdapter(new DemoDigitalAdapter("test-digital-adapter"));

            //Physical and Digital Adapters with Configuration
            //digitalTwin.addPhysicalAdapter(new DemoConfPhysicalAdapter("test-physical-adapter", new DemoPhysicalAdapterConfiguration()));
            machineForMesTwin.addDigitalAdapter(new DemoConfDigitalAdapter("test-digital-adapter", new DemoDigitalAdapterConfiguration()));

            // Create the Digital Twin Engine
            DigitalTwinEngine digitalTwinEngine = new DigitalTwinEngine();

            // Add the Digital Twin to the Engine
            digitalTwinEngine.addDigitalTwin(machineForMesTwin);

            // Set a new Event-Logger to a Custom One that we created with the class 'DemoEventLogger'
            WldtEventBus.getInstance().setEventLogger(new DemoEventLogger());

            // Start all the DTs registered on the engine
            digitalTwinEngine.startAll();

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
