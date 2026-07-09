"""
Module implementing a simple example transformation for the conceptual framework
described in our discussion. The function `F` computes the next state `S(t+1)`
of a system given the current state and several factors:

- S(t): current state of the system
- M(t): medium or environment
- V(t): vectors of transport (agents that carry state)
- L(t): latencies (delays in transport or response)
- P(t): selective pressures (forces that influence adaptation)
- R(t): memory or heritable components
- Z(t): latent variables or “esquecidos” (hidden factors not explicitly modeled)

The example below assigns arbitrary weights to each factor purely for
demonstration. In a real scientific context, these weights would be derived
from empirical data or theoretical considerations.
"""

from dataclasses import dataclass

@dataclass
class SystemState:
    """Data structure representing the state variables at time t."""
    s: float  # current state S(t)
    m: float  # medium/environment M(t)
    v: float  # vectors of transport V(t)
    l: float  # latencies L(t)
    p: float  # selective pressures P(t)
    r: float  # memory/heritable components R(t)
    z: float  # latent variables (esquecidos) Z(t)

def F(state: SystemState) -> float:
    """Compute the next state S(t+1) from the current state and influencing factors.

    This function uses weighted contributions from each factor. Positive weights
    indicate a factor increases the state, while negative weights indicate a
    decreasing influence. The choice of weights here is illustrative and does
    not reflect any specific scientific model.

    Args:
        state: SystemState object containing the variables at time t.

    Returns:
        The computed next state S(t+1) as a float.
    """
    return (
        state.s
        + 0.5 * state.m  # environment contributes positively
        + 0.3 * state.v  # transport vectors contribute positively
        - 0.2 * state.l  # latencies reduce the next state
        + 0.4 * state.p  # pressures contribute positively
        + 0.6 * state.r  # memory contributes positively
        - 0.3 * state.z  # hidden variables may reduce the next state
    )