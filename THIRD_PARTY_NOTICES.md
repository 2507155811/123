# Third-party software

Aero VPN is distributed under the GPLv2 terms and linking exceptions in LICENSE.

The embedded VPN implementation is OpenVPN for Android (Arne Schwabe), version
0.7.62, pinned to `9aee9b529a67cbc03dc10f9484d915b3440a2e81`.
Source: https://github.com/schwabe/ics-openvpn

The upstream native dependencies are fetched at the exact submodule revisions
recorded by that commit (OpenVPN, OpenSSL, LZO, LZ4, fmt). Each retains its own
upstream license. `scripts/prepare_engine.py` enables the upstream skeleton build
for OpenVPN 2 explicitly instead of relying on an old output-path regex. It also selects Android's API-23+ basename implementation, annotates already guarded modern APIs,
sets the Orbot receiver export flag, and fixes an upstream DNS log format string.
No cryptographic or certificate verification settings are weakened.

The AndroidX dependencies retain their Apache 2.0 license.
The dynamic node directory is provided by VPN Gate, University of Tsukuba, Japan:
https://www.vpngate.net/en/about_overview.aspx
Node location, latency, throughput and availability are reports from that service.
