# openDoJa

`openDoJa` is a desktop-focused clean-room reimplementation of the DoJa 5.1 runtime and related APIs, aimed at running decompiled i-appli / keitai Java games on modern computers while preserving the original compatibility-facing package structure.

## Status

The repository currently contains:

- the Java compatibility/runtime source under `src/`
- helper scripts under `scripts/`
- Maven metadata in `pom.xml`

Large reverse-engineering inputs and generated working notes are intentionally kept out of git.

## Build

```bash
mvn -q -DskipTests package
```

## Run

Open the desktop launcher UI:

```bash
java -jar target/opendoja-0.1.0-SNAPSHOT.jar
```

Launch a specific JAM directly through the packaged launcher:

```bash
java -jar target/opendoja-0.1.0-SNAPSHOT.jar --run-jam <game.jam>
```

For local development without packaging, the original CLI host path still works:

```bash
java -cp out/classes:<game-jar> opendoja.host.JamLauncher <game.jam>
```

## Reporting Broken Games

If a game does not work, please open a GitHub issue using the broken game report template.

Include:

- a text description of the issue
- the exact game that does not work
- screenshots or videos if they help explain the problem
- logs or stack traces if the issue is a crash

For the bundled local workflow used during development, see `scripts/`.
