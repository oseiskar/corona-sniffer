# BLE contact tracing sniffer PoC

How "anonymous" is the semi-decentralized BLE contact tracing provided by [Apple & Google](https://www.apple.com/covid19/contacttracing) (_GAEN_ a.k.a. _ENS_) or [DP-3T](https://github.com/DP-3T/documents)?

It is as anonymous as the simulated picture below - and this data is technically accessible to **any 3rd party who can install a large fleet of BLE-sniffing devices**. This is because all beacons signals broadcast by infected individuals are published to essentially all users of the system when an individual voluntarily uploads their positive infection status.

This repository contains a Proof-of-Concept implementation of a BLE-sniffing system that could uncover this data.

## Simulation

The image shows the results of a simulation where 400 BLE-sniffing devices would have been deployed in a 20×20 grid over an area of 1500×1500 m². The movement of 300 people around the area have been (crudely) simulated as random walks.

![simulated-data](.github/images/ble-sniffer-grid-simulation.png)

The red circles correspond beacon signals recorded from infected individuals, who have voluntarily uploaded their positive infection status to the local health authorities (5% of the paths in the simulation). Blue circles are signals recorded from other people using the contact tracing service. As illustrated by the lines connecting the red dots, the route traveled by each infected & announced individual can be reconstructed within the range of the sniffer grid.

See `backend/generate_fake_data.js` for details.

## System overview

A BLE-sniffing system consists of

 1. A significant number of BLE-sniffing devices installed to different known physical locations. An example implementation is given for Linux. Mobile phones could also act as BLE sniffers and an example app is provided for Android. They passively listen to contact tracing BLE traffic and upload it to the server(s).

 2. A device running an official local contact tracing app on a device which is hacked to intercept the confirmed positive _diagnosis keys_ (in [Apple/Google terminology][O1]) / _secret day keys_ (in [DP-3T][O2]) the app downloads from the local health authorities' servers. This part is not described in this repository, but it is an easy task and infeasible to prevent effectively.

 3. The server(s) receive the BLE contact tracing data, namely the _rolling proximity identifiers_ (RPIs, or _EphIds_ in DP-3T) from the agents and the _diagnosis keys_ from the hacked app.

[O1]: https://www.blog.google/documents/68/Android_Exposure_Notification_API_documentation_v1.2.pdf
[O2]: https://github.com/DP-3T/documents/blob/master/DP3T%20White%20Paper.pdf

## Linux agent

A simple BLE sniffer implementation for Linux. Listens to BLE messages from the GAEN and DP-3T protocols. The agent can verifiably observe DP-3T EphId payloads as broadcast by the [official test app][DP3TApp]. It can also observe traffic sent by the official [GAEN app in Finland][Koronavilkku].

[DP3TApp]: https://github.com/DP-3T/dp3t-app-android
[Koronavilkku]: https://koronavilkku.fi/

#### Installation

Tested on Debian Stretch

 1. Make sure the `hcidump` and `hcitool` BLE tools are installed, e.g.,
   on Debian: `sudo apt-get install bluez-hcidump`
 2. Run as root `cd linux; sudo ./run.sh`. See `run.sh` for more info. Requires Python 2.7.

## Android app

### Sniffer variant

An Android application that

 * displays the number of nearby GAEN devices on screen
 * reads and logs GAEN messages and their RSSis on the background
 * reads and logs the GPS & WiFi location data of the device it is installed in
 * runs in and Android Foreground Service until killed by the OS or closed with the back button
 * logs to the "external cache directory", which is not visible to other apps on the phone

Installed with `cd android; ./gradlew installSnifferDebug`.

The logs can be pulled from an USB-connected device using ADB:

    cd android; mkdir data; cd data
    adb pull /storage/emulated/0/Android/data/org.example.coronasniffer/cache/logs

The file `android/parse_logs.py` contains a script for parsing the logs and uploading
them to the backend, for eample:

    cd android
    cat data/logs/* | python parse_logs.py --server=http://localhost:3000

### Spoofer variant

A minimalistic app for sending various BLE beacon messages from an Android phone, including spoofed GAEN and DP-3T EphId payloads. The app is intended for more convenient testing without installing official contract tracing apps or their test versions.

The app can be installed through Android Studio or running `cd android; ./gradlew installSpooferDebug` - assuming you have working Android development environment installed. Make sure you have Bluetooth on in the phone and see Android Logcat for details of what the app is supposed to broadcast.

Note that it is possible that the spoofed messages broadcast by this app would be caught and recorded by actual contact tracing apps, but this should not cause any disturbance to the real contact tracing service. Those messages will effectively get ignored as they are never reported infected, similarly to the other "non-infected" traffic those apps see during their normal operation. However, do _not_ modify the app to spam the airwaves with very rapidly changing EphIds/RPIS, which could theoretically cause a Denial-of-Service to the nearby users.

## Backend server

A minimalistic server that can receive data from a fleet of sniffers, store it in a database, and visualize the results. HTTPS and authentication are currently not supported, but could be added using a reverse proxy. A single SQLite database cannot cope with a very high volume of data, but could be rather easily sharded.

#### Installation

 1. Setup NodeJS
 2. `cd backend`
 3. `npm install`
 4. `mkdir -p data`

By default, the data is stored to the SQLite file `backend/data/database.db`,
which can be directly examined/debugged with, e.g., the `sqlitebrowser` program.

#### Run simulation

 1. To populate the database with simulated data, run `npm run db:simulate`.
    This will erase any previous contents of the database.
 2. Run `npm run start`
 3. Go to http://localhost:3000 to observe the results.
    They will be shown on top of Open Street Map (using Leaflet)

#### Run on real devices

 0. Clear any previous data: delete `backend/data/database*`
 1. Start the server :`node index.js` (see the file for `BIND` and `PORT` options).
 2. Start the Linux agent(s): `cd linux; sudo ./run.sh`
    change `SERVER` in `run.sh` and the spec in `agent.json` as necessary.
 3. Run the Android test app to spoof Contact Tracing messages with a
    known _temporary exposure key_ (TEK)
 4. Open http://localhost:3000 (should show a blue circle)
 5. Mark the exposure key as infected, i.e., as a _diagnosis key_

        cd linux; ./resolve.py apple_google_en 6578616d706c65000000000000000000

 6. Refresh the browser, the circle should have turned red

**DISCLAIMER**: This repository is a Proof-of-Concept. Deploying this kind of a system _at scale_ would be a very bad idea for the following reasons:

 * It may be illegal. It very probably is under the GDPR/CCPA - unless you are a goverenmental entity who can argue it's for the greater good. Then different rules apply (also under the GDPR).
 * Even if it would be legal today, it could be illegal tomorrow. New legislation about the misuse of contact tracing data are being drafted in some countries.
 * This implementation is not tested or secure (no HTTPS or authentication and, e.g., the parsing Python scripts can crash on invalid payloads)
 * This implementation is AGPL-licensed (on purpose)

## Miscellaneous technical notes

The only difference between the GAEN [BLE specification][T1] and the Google Eddystone base protocol (cf. the diagram [here][T2]) is the 16-bit service UUID (0xFAAA vs 0xFD6F) and the BLE advertise flags (0x06 vs 0x1A). Devices that can observe Eddystone beacons can probably easily be adapted to read GAEN traffic.

In Google's reference implementation of the GAEN backend, the published key data is exported in a [modified protocol buffer format][T3], which can be parsed to JSON with the `linux/import_gaen_export.py` script in this repository.

[T1]: https://www.blog.google/documents/70/Exposure_Notification_-_Bluetooth_Specification_v1.2.2.pdf
[T2]: https://os.mbed.com/teams/Bluetooth-Low-Energy/code/BLE_EddystoneBeacon_Service/file/dfb7fb5a971b/Eddystone.h/
[T3]: https://github.com/google/exposure-notifications-server/blob/v0.7.0/internal/pb/export/export.proto

## More discussion

_See also my [blog post on Medium][D3] for a longer technical background story on BLE contact tracing and indoor positioning._

The data exposed by the BLE contact tracing systems is called _pseudonymous_: name, email, or other direct personal contact information is not known to the system, but the trails of locations themselves often single out an individual. For example, work and home locations are often easy to identify from the data.

BLE-sniffing devices have already been deployed (see, e.g., the [links here][D1]) for other purposes. In addition, any smart phone also has the technical ability to act as as one (for any third-party application, see [this preprint for existing examples][D4]), but Google & Apple can block this if this privacy issue is seen as more serious in the future. However, there are millions of devices controlled by other organizations which can potentially be transformed into BLE sniffers with an OTA software/firmware update. Laptops & cars for sure, new WiFi APs maybe, the rest of the Internet-of-Things - security cameras, fridges, cars, etc. - anyone's guess.

As a general principle, the legal consequences of exploiting a weakness, or the requirement that the attacker needs some budget for hardware, are usually not considered good defenses in cybersecurity. I think this issue is serious, and in a click-bait headline, I would call it a "side-channel attack vulnerability".

The designers of the various contact tracing systems see to be aware that this attack is possible, but it is unclear to me how serious they think it is. Concerns about this have been raised before (e.g, [here][D1] and [here][D2]). I have reported the existence of this PoC in the [DP-3T issue tracker][D1].

[D1]: https://github.com/DP-3T/documents/issues/43
[D2]: https://github.com/TCNCoalition/TCN/blob/ad400bc56d6b76e9fcec2901ae21206c0e2230ce/README.md#report-timespans-and-key-rotation
[D3]: https://medium.com/indooratlas/why-use-bluetooth-for-contact-tracing-1585feb024dc
[D4]: https://arxiv.org/pdf/2006.10719.pdf


## Changelog

 * _April 2020_: First version. Based on the spec only
 * _May 2020_: Longer readme & simulation. Confirmed to work with DP-3T and reported to the issue tracker. Published a link to this README in various channels.
 * _August 2020_: Confirmed to work with GAEN
 * _September 2020_: Added a fully working Android sniffer implementation, revised README
