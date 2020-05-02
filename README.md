## Linux agent

 1. Make sure the `hcidump` and `hcitool` BLE tools are installed, e.g.,
   on Debian: `sudo apt-get install bluez-hcidump`
 2. Run as root `cd linux; sudo ./run.sh`. See `run.sh` for more info.

**TODO**

 - [ ] parse rolling identifiers from the Apple/Google protocol
 - [ ] implement daily identifier -> rolling identifiers resolving

## Backend server

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
