# SvReMobile / ServerRelay — GitHub Student Developer Pack decision note

**Checked:** 26 October 2026. **Status:** informational planning resource; benefit eligibility and redemption vary by verified GitHub Education account. This note does not approve a production hosting provider.

## Current relevant offers (verify at redemption)

| Offer | Verified listed benefit | How to use it for ServerRelay/SvReMobile |
|---|---|---|
| Microsoft Azure for eligible students 18+ | $100 credit plus access to eligible Azure services; no credit card required per Pack listing | Optional dev/staging server after local Compose; monitor quotas and credit expiration |
| GitHub Pro | Free while eligible as a student | Repositories, reviews and project work |
| GitHub Copilot Student | Available to verified GitHub Education students, separate activation | IntelliJ/Android development assistance; review generated security-sensitive code |
| GitHub Codespaces | Pack lists free Pro-level access | Remote backend tasks/CI parity where quotas permit; not required for phone hotspot testing |
| JetBrains | Student subscription renewable annually | IntelliJ IDEA and Java/Kotlin toolchain |
| Namecheap | `.me` domain registration for 1 year and one SSL certificate for a year | Dev domain; prefer automated no-cost ACME TLS where appropriate; examine renewals |
| Name.com | Select free student domain extensions | Optional staging domain; check renewal cost |
| Datadog | Pro account including 10 servers for 2 years | **Later**, production-stage observability when privacy/retention approved |

**Not available for new redemptions:** DigitalOcean student credits sunset on 31 July 2026, existing offer credits expired by 1 August 2026 according to GitHub Education changelog. Do not select cloud based on that historical offer.

**Recommended path:**
1. Local ServerRelay via Docker Compose and Android devices on same development LAN. Keep mock HTTP cleartext **debug-scoped**, exclude it from release, and don't mistake local IP reachability for a public deployment.
2. Add backend contract and Gradle/Maven CI on GitHub; use student GitHub Pro/Codespaces/Copilot only if helpful.
3. Once reliable, choose staging VPS/managed provider, domain and HTTPS/WSS. Test whether Azure credits apply to the required container, database and egress services; cloud budget is **not** free by default.
4. Later introduce privacy-approved backend monitoring; keep mobile diagnostics local for the first milestone.

**Sources (primary):**
- Pack live offers: https://education.github.com/pack/
- GitHub Copilot Student activation: https://docs.github.com/en/copilot/how-tos/copilot-on-github/set-up-copilot/enable-copilot/set-up-for-students
- GitHub Education partner changelog (DigitalOcean exit): https://github.com/github-education-resources/Student-Developer-Pack-Current-Partners-FAQ/blob/main/SDP-changelog.md

**Google Play account fees and Play Console approval are separate from GitHub Education benefits.** New personal Google Play developer accounts (created after 13 Nov 2023) require 12 opted-in closed testers for 14 consecutive days before applying for production. Three engineering test phones are still useful but not enough by themselves for that approval gate; see https://support.google.com/googleplay/android-developer/answer/14151465 . Credits, promotions and published offers can change, and they are not production SLAs.
