"""Compatibility bridge to the canonical illustrative formula model.

Do not duplicate weights here. The implementation and its explicit
non-scientific/example boundary remain in ``engine.model``.
"""

from engine.model import F, SystemState

__all__ = ["F", "SystemState"]
