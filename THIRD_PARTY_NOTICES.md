# Third-party notices

SwitchNAT downloads the following pinned build dependency when VLESS + Reality support is built:

## AndroidLibXrayLite

- Version: `v26.11.9`
- Source: <https://github.com/2dust/AndroidLibXrayLite/tree/v26.11.9>
- Binary source: <https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.11.9>
- License: GNU Lesser General Public License v3.0
- License text: <https://github.com/2dust/AndroidLibXrayLite/blob/v26.11.9/LICENSE>

The downloaded AAR contains Xray Core components. Xray Core source and license information are available at:

- Source: <https://github.com/XTLS/Xray-core>
- License: Mozilla Public License 2.0
- License text: <https://github.com/XTLS/Xray-core/blob/main/LICENSE>

The AAR is not stored in this repository. `scripts/fetch_xray_core.sh` downloads the official release asset and verifies its pinned SHA-256 checksum. SwitchNAT does not modify AndroidLibXrayLite or Xray Core.
