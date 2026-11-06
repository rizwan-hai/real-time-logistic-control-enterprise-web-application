# GPS simulator

Provision devices in `tracking_device` with unique UUIDs and a SHA-256 hex digest of a random device token in `credential_hash`. Supply the raw tokens through the `GPS_SIMULATOR_DEVICES` environment variable as a JSON array:

```powershell
$env:GPS_SIMULATOR_DEVICES='[{"id":"DEVICE_UUID","token":"RANDOM_DEVICE_TOKEN"}]'
python scripts/gps-simulator/simulator.py --devices 1 --interval 2 --duration 120 --duplicate-rate 0.1
```

Use `--load` for a tight offered-load loop, `--batch-size` to send multiple positions per request, and `--interrupt-every`/`--interrupt-seconds` to simulate network outages. This tool reports offered client throughput; it is not a backend benchmark.
