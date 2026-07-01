from dataclasses import dataclass

@dataclass
class TechnicalScore:
    trend_score: float
    momentum_score: float
    volume_score: float
    volatility_score: float
    technical_score: float
    trend: str
    reasons: list
