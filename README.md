# openDoJa

`openDoJa` is a desktop-focused clean-room reimplementation of the DoJa 5.1 runtime and related APIs, aimed at running i-appli Java games on modern computers.

<p align="center">
  <img src=".github/showcase.png" alt="showcase" />
</p>

## Requirements
Java 22+

## Build

```bash
mvn -q -DskipTests package
```

## GitHub Actions

- Every push to `master` rebuilds the rolling `nightly` GitHub release and replaces its attached JAR.
- A GitHub release is created when the `pom.xml` version changes.

## Download

- Nightly (most up-to-date) version: https://github.com/GrenderG/openDoJa/releases/download/nightly/opendoja-nightly.jar
- Latest (stable) version: https://github.com/GrenderG/openDoJa/releases/latest

## Run

Open the desktop launcher UI:

```bash
java -jar opendoja-{version}.jar
```

Launch a specific JAM directly through the packaged launcher:

```bash
java -jar opendoja-{version}.jar --run-jam <game.jam>
```

Launch a JAM directly without the launcher UI, with explicit host scale, synth, user ID, and terminal ID:

```bash
java \
  -Dopendoja.hostScale=<x|fullscreen> \
  -Dopendoja.mldSynth=<fuetrek|ma3> \
  -Dopendoja.userId=<uid> \
  -Dopendoja.terminalId=<tid> \
  -jar opendoja-{version}.jar --run-jam <game.jam>
```

Print all available launcher options:

```bash
java -jar opendoja-{version}.jar --help
```

STAR support is experimental. A JAM containing an `AppType` field selects STAR (which is unique to star);
JAMs without that field use DoJa Profile. A compatibility layer is used over the shared graphics, audio,
input, network, and scratchpad. Other STAR APIs and mini-app behavior are not yet fully implemented.

## Reporting Broken Games

If a game does not work, please open a GitHub issue using the broken game report template.

Include:

- a text description of the issue
- the exact game that does not work
- screenshots or videos if they help explain the problem
- logs or stack traces if the issue is a crash

For the bundled local workflow used during development, see `scripts/`.

## Third-party Dependencies
- [jogl](https://github.com/sgothel/jogl)
- [input4j](https://github.com/gurkenlabs/input4j)
