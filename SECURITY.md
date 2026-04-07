# Security Policy

## Supported Usage

This repository is intended for active development and self-hosted deployment. Security fixes will generally be applied on the default branch first.

## Reporting a Vulnerability

Please do not open a public GitHub issue for a suspected secret leak or exploitable vulnerability.

Instead:

1. Prepare a minimal description of the issue.
2. Include reproduction steps, affected files or endpoints, and impact.
3. Send the report privately to the repository maintainer through the contact method listed on GitHub.

If private reporting contact information is not yet configured, avoid posting sensitive exploit details publicly and open a limited issue requesting a secure contact channel.

## What to Report

Examples:

- exposed API keys, tokens, or credentials
- unsafe secret handling in scripts or config templates
- authentication or authorization flaws
- SSRF, command injection, path traversal, or deserialization issues
- dependency vulnerabilities with real impact on this project

## Secrets Handling

The following must never be committed:

- `.env.travel-agent`
- real OpenAI or OpenAI-compatible provider keys
- real Amap / Gaode keys
- local runtime logs under `data/runtime`
- private certificates, keystores, or token files

If a secret is exposed:

1. rotate it immediately
2. remove it from the working tree
3. check whether it entered Git history
4. replace the affected credential in every environment that used it

## Hardening Notes

Current project safeguards already include:

- ignored local env files
- ignored common certificate and key file types
- production health-detail reduction under the `prod` profile
- configurable Amap HTTP request throttling

## Disclosure Expectations

Please allow reasonable time to validate and remediate a report before public disclosure.
