package it.barboneantonello.oven.utils;

import java.util.Objects;

public enum MachineState {
    START("START"),
    RESETTING("RESETTING"),
    READY("READY"),
    WORKING("WORKING"),
    BUSY("BUSY"),
    STOPPING("STOPPING"),
    STOPPED("STOPPED"),
    ANOMALY("ANOMALY"),
    INHERITED_ANOMALY("INHERITED_ANOMALY"),
    UNKNOWN("UNKNOWN"),
    END("END");

    private final String value;

    MachineState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MachineState fromString(String value) {
        for(MachineState state : MachineState.values()) {
            if(Objects.equals(state.getValue(), value)) {
                return state;
            }
        }

        throw new IllegalArgumentException("Invalid value: " + value);
    }
}
