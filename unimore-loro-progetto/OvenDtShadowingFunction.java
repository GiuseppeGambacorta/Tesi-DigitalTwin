package it.barboneantonello.oven.utils;

import it.barboneantonello.oven.config.DigitalTwinConfig;
import it.barboneantonello.oven.models.PhysicalPayloadMessage;
import it.wldt.adapter.digital.event.DigitalActionWldtEvent;
import it.wldt.adapter.physical.PhysicalAssetDescription;
import it.wldt.adapter.physical.event.PhysicalAssetEventWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetPropertyWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetRelationshipInstanceCreatedWldtEvent;
import it.wldt.adapter.physical.event.PhysicalAssetRelationshipInstanceDeletedWldtEvent;
import it.wldt.core.model.ShadowingFunction;
import it.wldt.core.state.*;
import it.wldt.exception.EventBusException;
import it.wldt.exception.ModelException;
import it.wldt.exception.WldtDigitalTwinStateException;
import it.wldt.exception.WldtDigitalTwinStatePropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.concurrent.ScheduledFuture;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class OvenDtShadowingFunction extends ShadowingFunction {

    // Logger
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final Logger logger = LoggerFactory.getLogger(OvenDtShadowingFunction.class);

    private static DigitalTwinConfig config;

    // OEE
    private static final Long IDEAL_PIECE_TIME = 217 * 100L;
    private static final Long MILLIS_PER_MINUTE = 60 * 1000L;
    private Long OEE_START_TIME = 0L;
    private Integer OEE_WORKSHIFT_TIME = 0;
    private final ScheduledExecutorService schedulerOEE = Executors.newScheduledThreadPool(1);
    private final Runnable taskOEE = this::calculateOEE;
    private ScheduledFuture<?> scheduledFuture = null;

    private Boolean iHadAnomaly = false;

    private CSVLogger csvLogger;

    // private DigitalTwinState previousState;

    MotorAnomalyDetection motorAnomalyDetection = new MotorAnomalyDetection(this::triggerAnomaly);

    public OvenDtShadowingFunction() throws WldtDigitalTwinStatePropertyException, IOException {
        super("oven-shadowing-function");
        try {
            InputStream input = new FileInputStream(new File("config.yaml"));
            Yaml yaml = new Yaml();
            config = yaml.loadAs(input, DigitalTwinConfig.class);
        } catch (FileNotFoundException e) {
            logger.error(RED + "File not found Exception loading yaml file: {}" + RESET, e.getMessage());
        }
    }

    @Override
    protected void onCreate() {

    }

    @Override
    protected void onStart() {

    }

    @Override
    protected void onStop() {

    }

    @Override
    protected void onDigitalTwinBound(Map<String, PhysicalAssetDescription> adaptersPhysicalAssetDescriptionMap) {
        logger.debug("Shadowing - onDtBound");

        try {
            startShadowing(adaptersPhysicalAssetDescriptionMap); // <--- passa la lista di tutti i PAD per tutti i PAdptrs
        } catch (WldtDigitalTwinStatePropertyException | IOException e) {
            throw new RuntimeException(e);
        }

        // qui mi metto in ascolto sui cambiamenti delle proprietà e degli eventi
        try
        {
            this.observePhysicalAssetProperties(adaptersPhysicalAssetDescriptionMap.values()
                    .stream()
                    .flatMap(pad -> pad.getProperties().stream())
                    .collect(Collectors.toList()));
            //observes all the available events
            this.observePhysicalAssetEvents(adaptersPhysicalAssetDescriptionMap.values()
                    .stream()
                    .flatMap(pad -> pad.getEvents().stream())
                    .collect(Collectors.toList()));
            this.observeDigitalActionEvents();
        } catch (EventBusException | ModelException e) {
            logger.error("Shadowing - onDigitalTwinBound", e);
        }

        try {
            updateMachineState();
        } catch (WldtDigitalTwinStatePropertyException | WldtDigitalTwinStateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onDigitalTwinUnBound(Map<String, PhysicalAssetDescription> adaptersPhysicalAssetDescriptionMap, String s) {

    }

    private void startShadowing(Map<String, PhysicalAssetDescription> adaptersPhysicalAssetDescriptionMap) throws WldtDigitalTwinStatePropertyException, IOException {
        try {

            // Per ogni PAD prendo Proprietà, eventi e azioni e le creo all'interno dello stato del DT
            this.digitalTwinStateManager.startStateTransaction();
            adaptersPhysicalAssetDescriptionMap.forEach((id, pad) -> {
                pad.getProperties().forEach(p -> {
                    try {

                        this.digitalTwinStateManager.createProperty(new DigitalTwinStateProperty<>(p.getKey(), p.getInitialValue()));

                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                });

                pad.getActions().forEach(a -> {
                    try {
                        this.digitalTwinStateManager.enableAction(new DigitalTwinStateAction(a.getKey(), a.getType(), a.getContentType()));
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                });

                pad.getEvents().forEach(event -> {
                    try {
                        this.digitalTwinStateManager.registerEvent(new DigitalTwinStateEvent(event.getKey(), event.getType()));
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                });
            });

            this.digitalTwinStateManager.createProperty(new DigitalTwinStateProperty<>("machine-state", MachineState.START));
            this.digitalTwinStateManager.createProperty(new DigitalTwinStateProperty<>("piece-count", 0));
            this.digitalTwinStateManager.createProperty(new DigitalTwinStateProperty<>("OEE", 0));
            this.digitalTwinStateManager.commitStateTransaction();

            observeDigitalActionEvents();

            notifyShadowingSync();

            try {
                motorAnomalyDetection.addMotorProperty(digitalTwinStateManager.getDigitalTwinState().getProperty("oven-carrier-clockwise"), 4000);
                motorAnomalyDetection.addMotorProperty(digitalTwinStateManager.getDigitalTwinState().getProperty("oven-carrier-counterclockwise"), 4000);

            } catch (WldtDigitalTwinStatePropertyException e) {
                throw new RuntimeException(e);
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        if (this.digitalTwinStateManager.getDigitalTwinState().getPropertyList().isPresent()) {
            for (DigitalTwinStateProperty<?> property : this.digitalTwinStateManager.getDigitalTwinState().getPropertyList().get()) {
                System.out.println(property.getKey() + ": " + property.getType());
            }
        }

        this.csvLogger = new CSVLogger(String.format("%s_logs.csv", this.digitalTwinStateManager.getDigitalTwinId()), this.digitalTwinStateManager.getDigitalTwinId(), this.digitalTwinStateManager.getDigitalTwinState().getPropertyList());
    }

    @Override
    protected void onPhysicalAdapterBidingUpdate(String s, PhysicalAssetDescription physicalAssetDescription) {

    }

    @Override
    protected void onPhysicalAssetPropertyVariation(PhysicalAssetPropertyWldtEvent<?> physicalAssetPropertyWldtEvent) {
        logger.info("Property " + BLUE + "'{}'" + RESET + " has a new value --> " + GREEN + "{}" + RESET, physicalAssetPropertyWldtEvent.getPhysicalPropertyId(), physicalAssetPropertyWldtEvent.getBody());

        try {
            // Store the current state before updating it (so it became the previous state later)
            //this.previousState = this.digitalTwinStateManager.getDigitalTwinState();

            DigitalTwinStateProperty<?> newProperty = new DigitalTwinStateProperty<>(physicalAssetPropertyWldtEvent.getPhysicalPropertyId(),
                                                                                     physicalAssetPropertyWldtEvent.getBody());


            assert this.digitalTwinStateManager.getDigitalTwinState().getProperty(physicalAssetPropertyWldtEvent.getPhysicalPropertyId()).isPresent();
            DigitalTwinStateProperty<?> oldProperty = this.digitalTwinStateManager.getDigitalTwinState().getProperty(physicalAssetPropertyWldtEvent.getPhysicalPropertyId()).get();


            // Ignore a new PhysicalPayloadMessage from MQTT that has a timestamp older than the already present one
            PhysicalPayloadMessage<?> new_msg;
            PhysicalPayloadMessage<?> old_msg;
            if (newProperty.getValue() instanceof PhysicalPayloadMessage) {
                new_msg = (PhysicalPayloadMessage<?>) newProperty.getValue();
                old_msg = (PhysicalPayloadMessage<?>) oldProperty.getValue();

                if ( new_msg.getTimestamp() < old_msg.getTimestamp() ) {
                    return;
                }

                if (!Objects.equals(newProperty.getValue(), oldProperty.getValue())) {
                    this.digitalTwinStateManager.startStateTransaction();
                    this.digitalTwinStateManager.updateProperty(newProperty);
                    this.digitalTwinStateManager.commitStateTransaction();

                    // Increment Piece Count
                    if (Objects.equals(newProperty.getKey(), "oven-heater")) {
                        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("machine-state").isPresent();
                        MachineState currentMachineState = (MachineState) this.digitalTwinStateManager.getDigitalTwinState().getProperty("machine-state").get().getValue();
                        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-heater").isPresent();
                        PhysicalPayloadMessage<Boolean> currentHeaterState = (PhysicalPayloadMessage<Boolean>) this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-heater").get().getValue();
                        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("piece-count").isPresent();
                        Integer currentPieceCount = (Integer) this.digitalTwinStateManager.getDigitalTwinState().getProperty("piece-count").get().getValue();

                        if (!currentHeaterState.getPayload() && currentMachineState == MachineState.WORKING) {
                            this.digitalTwinStateManager.startStateTransaction();
                            this.digitalTwinStateManager.updateProperty(new DigitalTwinStateProperty<>("piece-count", currentPieceCount + 1));
                            this.digitalTwinStateManager.commitStateTransaction();
                            logger.debug("PIECE COUNT: " + GREEN + "{}" + RESET, currentPieceCount + 1);
                        }
                    }

                    // Compute the new Machine State
                    updateMachineState();

                    motorAnomalyDetection.checkForAnomalies(newProperty);

                    try {
                        this.csvLogger.addLog(digitalTwinStateManager.getDigitalTwinState());
                    } catch (IOException | WldtDigitalTwinStatePropertyException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        } catch (WldtDigitalTwinStateException | WldtDigitalTwinStatePropertyException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMachineState() throws WldtDigitalTwinStatePropertyException, WldtDigitalTwinStateException {

        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-anomaly").isPresent();
        PhysicalPayloadMessage<Boolean> ovenStateAnomaly = (PhysicalPayloadMessage<Boolean>) this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-anomaly").get().getValue();

        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-resetting").isPresent();
        PhysicalPayloadMessage<Boolean> ovenStateResetting = (PhysicalPayloadMessage<Boolean>) this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-resetting").get().getValue();

        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-working").isPresent();
        PhysicalPayloadMessage<Boolean> ovenStateWorking = (PhysicalPayloadMessage<Boolean>) this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-working").get().getValue();

        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-busy").isPresent();
        PhysicalPayloadMessage<Boolean> ovenStateBusy = (PhysicalPayloadMessage<Boolean>) this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-busy").get().getValue();

        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-available").isPresent();
        PhysicalPayloadMessage<Boolean> ovenStateAvailable = (PhysicalPayloadMessage<Boolean>) this.digitalTwinStateManager.getDigitalTwinState().getProperty("oven-state-available").get().getValue();

        assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("machine-state").isPresent();
        MachineState currentMachineState = (MachineState) this.digitalTwinStateManager.getDigitalTwinState().getProperty("machine-state").get().getValue();

        DigitalTwinStateProperty<MachineState> newMachineState = null;

        // Compute the (eventually) new Machine State
        if (ovenStateAnomaly.getPayload() && currentMachineState != MachineState.ANOMALY && !ovenStateResetting.getPayload()) {
            if (iHadAnomaly) {
                newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.ANOMALY);
            } else {
                newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.INHERITED_ANOMALY);
            }
            iHadAnomaly = false;

        } else {

            try {
                switch (currentMachineState) {

                    case MachineState.START:
                        newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.UNKNOWN);
                        break;

                    case MachineState.UNKNOWN:
                        if (ovenStateResetting.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.RESETTING);
                        }
                        break;

                    case MachineState.RESETTING:
                        if (!ovenStateResetting.getPayload() && !ovenStateBusy.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.READY);
                        }
                        break;

                    case MachineState.READY:
                        if (ovenStateWorking.getPayload() && ovenStateBusy.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.WORKING);

                        } else if (!ovenStateAvailable.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.STOPPED);
                        }
                        break;

                    case MachineState.WORKING:
                        if (ovenStateBusy.getPayload() && !ovenStateWorking.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.BUSY);

                        } else if (!ovenStateWorking.getPayload() && !ovenStateBusy.getPayload() && !ovenStateResetting.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.READY);

                        } else if (!ovenStateAvailable.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.STOPPING);
                        }
                        break;

                    case MachineState.BUSY:
                        if (!ovenStateBusy.getPayload() && !ovenStateWorking.getPayload()) {
                            if (ovenStateResetting.getPayload()) {
                                newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.RESETTING);
                            } else {
                                newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.READY);
                            }

                        } else if (ovenStateWorking.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.WORKING);

                        } else if (!ovenStateAvailable.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.STOPPING);
                        }
                        break;

                    case MachineState.STOPPING:
                        if (!ovenStateBusy.getPayload() && !ovenStateWorking.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.STOPPED);
                        }
                        break;

                    case MachineState.STOPPED, MachineState.ANOMALY, MachineState.INHERITED_ANOMALY:
                        if (ovenStateResetting.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.RESETTING);

                        } else if (ovenStateAvailable.getPayload() && !ovenStateResetting.getPayload() && !ovenStateAnomaly.getPayload()) {
                            newMachineState = new DigitalTwinStateProperty<>("machine-state", MachineState.READY);
                        }
                        break;

                    case MachineState.END:
                        break;

                    default:
                        break;
                }

            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }

        if (newMachineState != null) {
            this.digitalTwinStateManager.startStateTransaction();
            this.digitalTwinStateManager.updateProperty(newMachineState);
            this.digitalTwinStateManager.commitStateTransaction();

            logger.info("New Machine State --> " + YELLOW + "{}" +  RESET, newMachineState.getValue());
        }
    }

    @Override
    protected void onPhysicalAssetEventNotification(PhysicalAssetEventWldtEvent<?> physicalAssetEventWldtEvent) {

    }

    @Override
    protected void onPhysicalAssetRelationshipEstablished(PhysicalAssetRelationshipInstanceCreatedWldtEvent<?> physicalAssetRelationshipInstanceCreatedWldtEvent) {

    }

    @Override
    protected void onPhysicalAssetRelationshipDeleted(PhysicalAssetRelationshipInstanceDeletedWldtEvent<?> physicalAssetRelationshipInstanceDeletedWldtEvent) {

    }

    @Override
    protected void onDigitalActionEvent(DigitalActionWldtEvent<?> digitalActionWldtEvent) {
        logger.debug("Received DIGITAL action " + BLUE + "'{}'" + RESET + " with content: " + YELLOW + "{}" + RESET, digitalActionWldtEvent.getActionKey(), digitalActionWldtEvent.getBody());

        try {
            if (digitalActionWldtEvent.getActionKey().equals("start-oee")) {
                startOEEComputation(digitalActionWldtEvent);
            }

            this.publishPhysicalAssetActionWldtEvent(digitalActionWldtEvent.getActionKey(), digitalActionWldtEvent.getBody());

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void triggerAnomaly() throws EventBusException {
        logger.info(RED + "Anomaly Detected!" + RESET);
        this.publishPhysicalAssetActionWldtEvent("controls", "emergency-stop");
        iHadAnomaly = true;
    }

    private void startOEEComputation(DigitalActionWldtEvent<?> digitalActionWldtEvent) throws WldtDigitalTwinStateException {
        Gson gson = new Gson();
        Map<String, Integer> oeeSettings = gson.fromJson((String) digitalActionWldtEvent.getBody(), new TypeToken<Map<String, Integer>>(){}.getType());
        OEE_WORKSHIFT_TIME = oeeSettings.get("workShiftMinutes");

        logger.info("WORKSHIFT TIME: " + GREEN + "{}" + RESET, OEE_WORKSHIFT_TIME);

        DigitalTwinStateProperty<Double> newOEEProperty = new DigitalTwinStateProperty<>("OEE", -1.0);
        DigitalTwinStateProperty<Integer> newPiecesDoneProperty = new DigitalTwinStateProperty<>("piece-count", 0);

        this.digitalTwinStateManager.startStateTransaction();
        this.digitalTwinStateManager.updateProperty(newOEEProperty);
        this.digitalTwinStateManager.updateProperty(newPiecesDoneProperty);
        this.digitalTwinStateManager.commitStateTransaction();

        OEE_START_TIME = 0L;

        // Execute Thread that calculate OEE every seconds starting 20 seconds after DT start
        if(scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = schedulerOEE.scheduleAtFixedRate(taskOEE, 2, 2, TimeUnit.SECONDS);

    }

    private void calculateOEE() {
        try {
            if(OEE_START_TIME == 0) {
                OEE_START_TIME = System.currentTimeMillis();
            }

            assert this.digitalTwinStateManager.getDigitalTwinState().getProperty("piece-count").isPresent();
            Integer piecesDone = (Integer) this.digitalTwinStateManager.getDigitalTwinState().getProperty("piece-count").get().getValue();

            DigitalTwinStateProperty<Double> newOEEProperty = null;

            if(System.currentTimeMillis() <= OEE_START_TIME + (MILLIS_PER_MINUTE * OEE_WORKSHIFT_TIME)) {
                //Double OEE = (double) (piecesDone * IDEAL_PIECE_TIME) / (MILLIS_PER_MINUTE * OEE_WORKSHIFT_TIME);
                Double OEE = (double) (piecesDone * IDEAL_PIECE_TIME) / (System.currentTimeMillis() - OEE_START_TIME);
                newOEEProperty = new DigitalTwinStateProperty<>("OEE", OEE);
            }

            if(newOEEProperty != null) {
                this.digitalTwinStateManager.startStateTransaction();
                this.digitalTwinStateManager.updateProperty(newOEEProperty);
                this.digitalTwinStateManager.commitStateTransaction();
                try {
                    this.csvLogger.addLog(digitalTwinStateManager.getDigitalTwinState());
                } catch (IOException | WldtDigitalTwinStatePropertyException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (WldtDigitalTwinStatePropertyException | WldtDigitalTwinStateException e) {
            throw new RuntimeException(e);
        }
    }

    public static DigitalTwinConfig getConfig() {
        return config;
    }
}
