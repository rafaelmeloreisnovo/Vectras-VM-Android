# Documentation audit tools

This directory contains source-read-only tooling for bounded documentation and ingress audits.

## Tools

- `audit_documentation_state.py` scans the repository up to a fixed depth, reports placeholder/bug signals, ingress folders, overlays and navigation gaps, then writes Markdown and JSON reports, and regenerates the ingress SHA-256 TSV/Markdown manifest.

## Usage

```bash
./tools/docs/audit_documentation_state.py --max-depth 5
```

The tool does not edit source modules, assembly, hot paths, runtime code or ingress artifacts. It only refreshes the configured audit/manifest outputs under `docs/organization/` and `reports/`.
