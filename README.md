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
./scripts/build.sh
```

## Run

Launch a DoJa application through the desktop host:

```bash
java -cp out/classes:<game-jar> opendoja.host.JamLauncher <game.jam>
```

For the bundled local workflow used during development, see `scripts/`.
