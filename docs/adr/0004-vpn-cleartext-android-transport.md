# ADR 0004: VPN-only cleartext Android transport

- Status: Accepted
- Date: 2026-08-24

## Decision

EasyMacros permits cleartext HTTP in Android debug and release builds by setting
`android:usesCleartextTraffic="true"` in the main application manifest. Release HTTP is
supported only when the API address is reachable exclusively through an encrypted VPN.
Deployments without that enforced routing boundary must use HTTPS.

The application does not attempt to detect, require, or attest a VPN connection.
Enforcement belongs to deployment networking: bind or proxy the API on the VPN interface,
firewall it from public and ordinary LAN interfaces, and avoid addresses that can resolve
or route outside the tunnel.

## Consequences

This supports private deployments without operating an additional TLS endpoint. Android
network security no longer prevents an accidental HTTP configuration in release builds.
HTTP provides no application-layer encryption, so bearer tokens, ingredient data, and
uploaded images rely entirely on the VPN tunnel. A routing or firewall mistake can expose
those values; HTTPS remains the safer default for any endpoint not provably VPN-only.
