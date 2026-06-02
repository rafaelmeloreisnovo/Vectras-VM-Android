# Documentation audit tools

This directory contains read-only tooling for bounded documentation and ingress audits.

## Tools

- `audit_documentation_state.py` scans the repository up to a fixed depth, reports placeholder/bug signals, ingress folders, overlays and navigation gaps, then writes Markdown and JSON reports.

## Usage

```bash
./tools/docs/audit_documentation_state.py --max-depth 5
```

The tool does not edit source modules, assembly, hot paths or runtime code. It only refreshes the configured audit reports under `docs/organization/` and `reports/`.
