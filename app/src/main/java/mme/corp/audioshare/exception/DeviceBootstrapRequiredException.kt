package mme.corp.audioshare.exception

class DeviceBootstrapRequiredException : IllegalStateException(
    "Device bootstrap is required before sending presence heartbeat"
)
