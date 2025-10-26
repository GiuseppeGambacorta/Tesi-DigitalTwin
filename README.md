# Digital Twin Project con WLDT

Questo progetto dimostra l'utilizzo della libreria **WLDT (White Label Digital Twin)** versione 0.4.0 per creare un Digital Twin in Java.

## Struttura del Progetto

```
DigitalTwin/
├── build.gradle                 # Configurazione Gradle con dipendenza WLDT
├── gradlew                      # Script Gradle Wrapper per Unix/Linux
├── gradlew.bat                  # Script Gradle Wrapper per Windows
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/digitaltwin/
│   │   │       ├── Main.java                        # Classe principale
│   │   │       └── adapter/
│   │   │           ├── ExamplePhysicalAdapter.java  # Physical Adapter di esempio
│   │   │           └── ExampleDigitalAdapter.java   # Digital Adapter di esempio
│   │   └── resources/
│   │       └── logback.xml                         # Configurazione logging
│   └── test/
│       └── java/
│           └── com/example/digitaltwin/
│               └── MainTest.java                    # Test di esempio
└── README.md                                        # Questo file
```

## Caratteristiche

- **WLDT Core 0.4.0**: Implementazione del framework Digital Twin
- **Physical Adapter**: Simulazione di un dispositivo fisico con proprietà (temperatura, umidità), azioni (accensione/spegnimento) ed eventi (allarmi)
- **Digital Adapter**: Gestione della rappresentazione digitale e logica di business
- **Logging**: Configurazione completa con Logback per console e file
- **Testing**: Test unitari con JUnit 5

## Prerequisiti

- **Java 11** o superiore
- **Gradle** (incluso Gradle Wrapper)

## Come Eseguire

### 1. Compilazione del progetto
```bash
# Su Windows
.\gradlew build

# Su Unix/Linux/Mac
./gradlew build
```

### 2. Esecuzione dell'applicazione
```bash
# Su Windows
.\gradlew run

# Su Unix/Linux/Mac
./gradlew run
```

### 3. Esecuzione dei test
```bash
# Su Windows
.\gradlew test

# Su Unix/Linux/Mac
./gradlew test
```

## Funzionalità WLDT Implementate

### Physical Adapter
- **Proprietà**: 
  - `temperature`: Valore della temperatura (simulato)
  - `humidity`: Valore dell'umidità (simulato)
- **Azioni**:
  - `switch_on`: Accende il dispositivo
  - `switch_off`: Spegne il dispositivo
- **Eventi**:
  - `alarm`: Eventi di allarme casuali

### Digital Adapter
- Monitoraggio delle proprietà in tempo reale
- Gestione delle azioni digitali
- Logica di business (es. allarme per temperatura alta)
- Logging dettagliato degli stati

## Configurazione

La configurazione del WLDT Engine include:
- **Device Namespace**: `com.example.digitaltwin`
- **Base Identifier**: `digital-twin-demo`
- **Startup Time**: 10 secondi
- **Application Metrics**: Abilitato

## Logging

I log vengono salvati in:
- **Console**: Output formattato con timestamp
- **File**: `logs/digital-twin.log` con rotazione giornaliera

## Dipendenze Principali

- `io.github.wldt:wldt-core:0.4.0` - Framework WLDT
- `ch.qos.logback:logback-classic` - Sistema di logging
- `org.junit.jupiter:junit-jupiter` - Framework di testing

## Sviluppo Futuro

Per estendere questo progetto, puoi:
1. Implementare connettori per dispositivi IoT reali
2. Aggiungere più Digital Adapter per diverse rappresentazioni
3. Integrare con sistemi di monitoraggio esterni
4. Implementare API REST per l'accesso ai dati del Digital Twin
5. Aggiungere persistenza dei dati

## Riferimenti

- [WLDT Documentation](https://github.com/wldt/wldt-core)
- [Digital Twin Concepts](https://en.wikipedia.org/wiki/Digital_twin)
