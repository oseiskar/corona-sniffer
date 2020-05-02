# BLE contact tracing sniffer PoC

How "anonymous" is BLE contact tracing as proposed by [Apple & Google](https://www.apple.com/covid19/contacttracing)? A picture is worth more than a thousand words - it's about as anonymous as the simulated picture below - and this data is technically accessible to **any 3rd party who can install a large fleet of BLE-sniffing devices**, _not_ just the health authorities.

![simulated-data](.github/images/ble-sniffer-grid-simulation.png)
_Simulated grid of 400 BLE sniffers over downtown Helsinki_

The red circles correspond beacon signals recorded from infected individuals, who have voluntarily uploaded their positive infection status to the local health authorities. Blue circles are signals recorded from other people using the contact tracing service. As illustrated by the lines connecting the red dots, the route traveled by each infected & announced individual can be reconstructed within the range of the sniffer grid.

This type of personal location data is called _pseudonymous_: the name or other personal information, is not known to the system, but as typical with pseudonymous location data, the locations may easily single out the individual (work & home locations can often be easily identified from this kind of data).

This repository contains a Proof-of-Concept implementation for a BLE sniffing system that could uncover this data (some parts still TO-DO). It's unclear to me how serious the various designers of the system (DP-3T, Google, Apple) think this weakness is. Is it a bug or a feature? If I had to select a click-bait headline, I would go for "side-channel attack vulnerability". Concerns about this have been raised before.

Instead of dedicated BLE-sniffing devices, any smart phone also has the technical ability to act as a sniffing device (for any third-party application), but Google & Apple can block this if this privacy issue is seen as more serious in the future. However, there are millions of other devices that they do not control and which can potentially be transformed into BLE sniffers with an OTA software/firmware update. Laptops & cars for sure, new WiFi APs maybe, the rest of the Internet-of-Things - security cameras, fridges, cars, etc. - anyone's guess.

Note that using / deploying this is kind of a system "just because you can" could be illegal under many legislations - especially under the GDPR/CCPA. New legislation about misuse of the contact tracing data is also being drafted in some countries. However, even under the GDPR, different rules apply under governmental entities if they argue they need it for the greater good. In any case, the legal consequences of exploiting a weakness are usually not considered a good defense in cybersecurity.

## Linux agent

A simple BLE sniffer implementation for Linux.

 1. Make sure the `hcidump` and `hcitool` BLE tools are installed, e.g.,
   on Debian: `sudo apt-get install bluez-hcidump`
 2. Run as root `cd linux; sudo ./run.sh`. See `run.sh` for more info.

**TODO**

 - [ ] parse rolling identifiers from the Apple/Google protocol
 - [ ] implement daily identifier -> rolling identifiers resolving

## Backend server

A minimalistic server that can receive data from a fleet of sniffers, store it in a database, and visualize the results.

**Installation**

 1. Setup NodeJS
 2. `cd backend`
 3. `npm install`
 4. `mkdir -p data`

By default, the data is stored to the SQLite file `backend/data/database.db`,
which can be directly examined/debugged with, e.g., the `sqlitebrowser` program.

To populate the database with fake / simulated data, for testing,
run `node generate_fake_data.js`. This will erase any previous contents of the
database.

**Run**

    node index.js
    # go to http://localhost:3000
