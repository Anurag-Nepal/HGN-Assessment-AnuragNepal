#!/usr/bin/env python3
import json, sys
from datetime import datetime, timezone
import paho.mqtt.publish as publish

def main():
    device_id = sys.argv[1] if len(sys.argv) > 1 else "DEV123"
    broker = sys.argv[2] if len(sys.argv) > 2 else "localhost"
    payload = {
        "deviceId": device_id, "latitude": 27.7172, "longitude": 85.3240,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    publish.single(topic=f"sos/{device_id}", payload=json.dumps(payload), qos=1, hostname=broker)
    print(f"Published SOS for {device_id} at {payload['timestamp']}")

if __name__ == "__main__":
    main()