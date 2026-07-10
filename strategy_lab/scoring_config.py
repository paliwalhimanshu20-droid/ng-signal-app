"""
strategy_lab/scoring_config.py — PR 8 continuation, Requirement 1.

Research Score weights ONLY. Kept in its own module, separate from
strategy_lab/research_score.py's formula logic, so weights can be
adjusted later without touching (or redeploying different logic in) the
scoring engine itself — exactly the "adjustable from configuration
without redesigning the engine" requirement.

The four components and why these four:
  - WIN_RATE_WEIGHT: the most direct answer to "does this work" — how
    often the strategy was profitable.
  - SAMPLE_SIZE_WEIGHT: a win rate from 5 trades and a win rate from 500
    trades are not equally trustworthy. This component rewards larger
    sample sizes, with diminishing returns past a target size (see
    SAMPLE_SIZE_TARGET below) rather than rewarding infinite trade counts.
  - PROFIT_FACTOR_WEIGHT: win rate alone doesn't capture whether wins are
    bigger than losses — profit factor (gross profit / gross loss) does.
  - REGIME_CONSISTENCY_WEIGHT: rewards a strategy that performs
    reasonably across MULTIPLE market regimes, not just one lucky
    regime — see strategy_lab/research_score.py for the exact
    consistency calculation.

Weights sum to 100 by construction (see research_score.py's assertion).
Change any value below to re-weight the score; no other file needs to
change.
"""

WIN_RATE_WEIGHT = 40
SAMPLE_SIZE_WEIGHT = 20
PROFIT_FACTOR_WEIGHT = 20
REGIME_CONSISTENCY_WEIGHT = 20

# Sample size at which SAMPLE_SIZE_WEIGHT's component reaches its full
# value (diminishing returns beyond this — see research_score.py). Not
# derived from anything statistical (e.g. a confidence-interval
# calculation) — a documented, round-number placeholder pending real
# production trade-count data to calibrate against. Flagged here, not
# hidden, so it's an easy, obvious thing to revisit.
SAMPLE_SIZE_TARGET = 200

# Profit factor at which PROFIT_FACTOR_WEIGHT's component reaches its
# full value. 2.0 (gross profit = 2x gross loss) is a commonly cited
# "good" profit factor threshold in trading literature — not tuned to
# this dataset.
PROFIT_FACTOR_TARGET = 2.0
