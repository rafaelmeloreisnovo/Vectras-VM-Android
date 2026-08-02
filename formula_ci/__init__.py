"""Compatibility package for the formula validation workflow.

The canonical illustrative implementation remains in :mod:`engine.model`.
This package exists so historical imports of ``formula_ci.model`` continue to
resolve without maintaining a second formula implementation.
"""

from .model import F, SystemState

__all__ = ["F", "SystemState"]
