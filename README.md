# Karoo Power Compare

A [Hammerhead Karoo](https://www.hammerhead.io/) extension to compare two power meters during a ride.

- **Meter A** is the power meter paired with the Karoo as usual (ANT+ or BLE).
- **Meter B** is a second **BLE** power meter that this extension connects to.

## Data fields

| Field | What it shows |
|---|---|
| Power B | Instant power from meter B |
| Power Δ 3s | B − A in watts, 3-second average |
| Power Δ% 3s | (B − A) / A in percent, 3-second average. Blank below 30 W |
| Ride Power Δ% | (B − A) / A over the whole recorded ride |

A positive value means meter B reads higher than meter A.

The ride's FIT file also gets developer fields:

- `power_b`: meter B's power, on every record
- `power_b_vs_a`: the ride difference in percent, on the session
- `avg_power_b`: meter B's ride average, on the session

You can use them for a second-by-second analysis afterwards, for example in DC Rainmaker's Analyzer or a script.

## Setup on the Karoo

1. Install the APK (see below).
2. Pair meter A with the Karoo as a normal power meter.
3. In **Sensors → Add sensor**, pick the **Power B …** entry this extension finds.
   - Do **not** also pair meter B as a regular Karoo power meter.
4. Add the Power Compare fields to a ride page.

### Things to know

- **BLE connection limits:** many power meters accept only one or two BLE connections. Disconnect meter B from phone apps and other head units.
- **Single-sided meters:** if you compare a left-only meter with a dual-sided one, the difference includes your left/right imbalance.
- **Before comparing:** zero-offset (calibrate) both meters before the ride.

## Install

Releases publish an APK and a `manifest.json` that the Karoo uses for updates.

- Sideload it with `adb install karoo-power-compare.apk`, or
- open the release APK link in the Hammerhead Companion app.

## Development

You need JDK 17 and the Android SDK (compileSdk 35).

Hammerhead's `karoo-ext` library is published on GitHub Packages. GitHub Packages requires a token with `read:packages`, even for public packages:

```sh
gh auth refresh -h github.com -s read:packages
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
USERNAME=<github user> TOKEN=$(gh auth token) ./gradlew testDebugUnitTest assembleDebug
```

You can also set `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`.

To install on a Karoo connected over USB:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Push a tag `v<versionName>`.

The release workflow builds the APK and attaches it with `manifest.json` to a GitHub release.

### Layout

- `ble/`: BLE scanning/connection (adapted from Hammerhead's karoo-ext sample) and the Cycling Power Measurement parser
- `compare/`: the comparison math. It is plain Kotlin and unit tested
- `datatypes/`: the Karoo data fields
- `fit/`: FIT developer field definitions
- `PowerCompareExtension.kt`: the Karoo extension service that wires it together

## License

Apache 2.0
