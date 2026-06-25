# RAFAELIA Operational State Buffer

This document defines an operational stability model for Vectras-VM-Android.

## Core model

```text
system survival = structural capacity * monitoring * buffer * return path
```

Correct stability form:

```text
Basal + Buffer + Return
```

Definitions:

```text
Basal  = normal operating zone
Buffer = margin that absorbs pressure without collapse
Return = mechanism that restores safe operation after perturbation
```

For Android/VM:

```text
Basal  = expected CPU, RAM, storage, thermal, scheduler, IO
Buffer = free memory, thermal headroom, disk reserve, watchdog window, queue margin
Return = cleanup, restart, rollback, throttling, checkpoint, state restore
```

## Operational failure pattern

```text
resource exists
monitor does not see it
process reacts incorrectly
VM stalls or thrashes
```

Therefore documentation must separate:

```text
real support
perceived support
reaction mode
recovery path
```

## State modes

```text
MODE_BASELINE
MODE_PRESSURE
MODE_THROTTLE
MODE_RECOVERY
MODE_FAILSAFE
MODE_AUDIT
```

- MODE_BASELINE: normal envelope.
- MODE_PRESSURE: resource pressure detected, buffer remains.
- MODE_THROTTLE: reduce work before chaos.
- MODE_RECOVERY: release resources and restore checkpoint.
- MODE_FAILSAFE: stop unsafe cascade and preserve logs.
- MODE_AUDIT: emit state, hashes, metrics, and claim boundary.

## Watchdog/CRC/TTL doctrine

```text
watchdog = integrity perception
CRC/hash = state verification
TTL = bounded lifetime of trust
checkpoint = return path
rollback = refusal to continue corrupted state
```

A signal should not be accepted only because it is loud.

```text
input + integrity + context + state = actionable event
```

## Android/VM measurement checklist

```text
device model
Android version
ABI
kernel
available storage
RAM pressure
CPU load
thermal state
process count
VM configuration
QEMU/UserLAnd/proot mode
crash log
ANR/logcat
artifact checksum
```

If unknown:

```text
[GAP] not measured
[TOKEN_VAZIO] intentionally empty; do not infer
```

## Pressure-to-action matrix

| Condition | Meaning | Action |
|---|---|---|
| low RAM | buffer shrinking | reduce workload, checkpoint |
| high thermal | chaos cost rising | throttle or pause |
| low storage | artifact risk | cleanup before build |
| repeated crash | state unstable | fail-safe and audit |
| missing ABI | build mismatch | stop and document gap |
| stale docs | code beyond docs | generate gap ledger |

## Documentation rule

Every operational doc should answer:

```text
What is running?
Where is it running?
What ABI/kernel/runtime?
What can be measured?
What is assumed?
What failed?
What is the next safe action?
```

If any answer is unknown, mark `TOKEN_VAZIO` and do not invent.

## One-line doctrine

```text
A VM survives not by maximum force, but by measured pressure, buffer, and safe return.
```
