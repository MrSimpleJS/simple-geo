# Simple-GEO

Simple-GEO is a Velocity proxy plugin that combines GeoIP filtering, VPN/proxy detection, temporary anti-bot bans and maintenance-mode handling.

## Requirements

- Java 25 or newer
- Velocity 3.4.x

GeoLite2 Country and IP2Proxy LITE PX2 are included in the release JAR. No separate database download is required.

## Installation

1. Download `Simple-GEO-1.0.0.jar` from the GitHub release.
2. Copy it into the Velocity `plugins` directory.
3. Start Velocity. Simple-GEO creates `plugins/simple-geo/` and extracts both bundled databases.
4. Review `plugins/simple-geo/config.properties` and restart Velocity after making changes.

### Bundled databases

During the first startup, Simple-GEO extracts these files from its own JAR:

- `plugins/simple-geo/GeoLite2-Country.mmdb`
- `plugins/simple-geo/IP2PROXY-LITE-PX2.CSV`

Extraction progress is displayed in the proxy console. Existing files are never overwritten, and no external database URL, MaxMind account or license key is required. Set `database-auto-download=false` to disable automatic extraction and provide the files manually.

## Configuration

| Key | Default | Description |
| --- | --- | --- |
| `language` | `en` | Message language. Use `en` for English or `de` for German. |
| `allowed-countries` | `DE,AT,CH,LU,LI,BE,NL` | Comma-separated ISO country codes allowed to join. |
| `ip2proxy-file` | `IP2PROXY-LITE-PX2.CSV` | IP2Proxy CSV filename in the plugin data directory. |
| `database-auto-download` | `true` | Extracts missing bundled database files during startup. |
| `ipwhois-enabled` | `true` | Enables ISP lookup through ipwho.is when the CSV has no ISP. |
| `ipwhois-timeout-ms` | `1500` | Connection and read timeout, with a minimum of 100 ms. |
| `blocked-cidrs` | bundled list | Comma-separated IPv4 or IPv6 CIDRs to block. |
| `blocked-isps` | bundled list | Semicolon-separated ISP names to block. |
| `maintenance` | `false` | Enables maintenance mode. |
| `maintenance-eta` | `unknown` | ETA shown in the maintenance message. |
| `botban-sync-url` | empty | Endpoint receiving the bot-ban properties file. |
| `botban-sync-secret` | empty | Bearer token for the bot-ban sync endpoint. |
| `botban-sync-interval-seconds` | `60` | Upload interval; `0` disables it. |
| `botban-unban-sync-url` | empty | Endpoint returning bot-ban IDs to remove. |
| `botban-unban-sync-secret` | empty | Bearer token for the unban endpoint. |
| `botban-unban-sync-interval-seconds` | `30` | Unban poll interval; `0` disables it. |

## Languages

On first startup, the plugin creates:

- `plugins/simple-geo/lang/en.properties`
- `plugins/simple-geo/lang/de.properties`

English is enabled by default. Set `language=de` in `config.properties` to use German, then restart Velocity. Both files can be customized; existing files are never overwritten during startup. All kick, MOTD, command, staff and hover messages support legacy `&` color codes and named placeholders such as `{name}`, `{id}`, `{ip}`, `{eta}` and `{duration}`.

## Commands and permissions

| Command | Permission | Purpose |
| --- | --- | --- |
| `/geo-allow <id>` | `simplegeo.allow` | Whitelists the IP from a reported GeoIP block. |
| `/bot-allow <id>` | `simplegeo.botallow` | Removes a temporary bot ban. |
| `/maintenance-eta <text>` | staff groups | Changes the maintenance ETA. |
| `/maintenance-allow` | staff groups | Shows the current maintenance access policy. |

The previous command names `/geoallow` and `/botallow` remain available as compatibility aliases.

Additional permissions:

- `simplegeo.bypass`: bypasses GeoIP and most proxy/anti-bot checks.
- `antibot.bypass`: bypasses proxy and anti-bot checks except explicitly blocked ISPs.
- `simplegeo.notify`: receives staff notifications.
- `group.admin`, `group.mod`, `group.cadmin`: staff access used by maintenance mode and commands.

## Build

```bash
mvn clean package
```

The release-ready shaded JAR is written to `target/Simple-GEO-1.0.0.jar`.

## Project structure

```text
de/mrsimplejs/simplegeo/
├── SimpleGeoPlugin.java          Velocity entry point and lifecycle
├── commands/                     Command registration and handlers
├── config/                       Configuration, languages and properties
├── model/                        Bot bans, GeoIP blocks, ranges and cache entries
├── repository/                   Persistent bot-ban and whitelist state
├── service/                      GeoIP, CIDR, IP2Proxy and ISP services
└── util/                         HTTP and network utilities
```

The source folders are grouped by responsibility under the `de.mrsimplejs.simplegeo` package.

## Privacy

> [!WARNING]
> The plugin can store player UUIDs, names, IP addresses and ISP information in its data directory. If ipwho.is lookup is enabled, player IP addresses are sent to that service. Server owners are responsible for providing required privacy notices and retention rules.

## License

MIT License. See [LICENSE](LICENSE).

The bundled GeoLite2 data is provided by MaxMind. The bundled IP2Proxy LITE data is provided by IP2Location and requires attribution according to its applicable license. Database rights and licenses remain with their respective providers.

Author: **MrSimpleJS**
