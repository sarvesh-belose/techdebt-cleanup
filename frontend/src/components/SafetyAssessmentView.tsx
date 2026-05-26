interface SignalResult {
  signalName: string;
  score: number;
  reasons: string[];
}

interface Assessment {
  totalScore: number;
  threshold: number;
  blocked: boolean;
  signals: SignalResult[];
}

interface Props {
  /** Raw JSON stored on FixAttempt.safetySignalsJson. */
  signalsJson: string | null;
  /** Convenience: textual summary from FixRun.safetyBlockedReason. */
  summary?: string | null;
}

export function SafetyAssessmentView({ signalsJson, summary }: Props) {
  let assessment: Assessment | null = null;
  if (signalsJson) {
    try {
      assessment = JSON.parse(signalsJson) as Assessment;
    } catch {
      assessment = null;
    }
  }

  if (!assessment) {
    return summary ? (
      <p className="whitespace-pre-wrap text-sm text-slate-700">{summary}</p>
    ) : (
      <p className="text-sm text-slate-500">No safety assessment recorded.</p>
    );
  }

  const pct = Math.min(100, Math.round((assessment.totalScore / 100) * 100));
  const barColor =
    assessment.totalScore >= assessment.threshold ? 'bg-red-500' : 'bg-emerald-500';

  return (
    <div className="space-y-4">
      <div>
        <div className="flex items-baseline justify-between">
          <div className="text-sm font-medium text-slate-700">
            Score: <span className="text-lg font-semibold">{assessment.totalScore}</span> / 100
          </div>
          <div className="text-xs text-slate-500">
            threshold {assessment.threshold} · {assessment.blocked ? 'BLOCKED' : 'PASSED'}
          </div>
        </div>
        <div className="mt-2 h-2 w-full overflow-hidden rounded bg-slate-100">
          <div className={`h-2 ${barColor}`} style={{ width: `${pct}%` }} />
        </div>
      </div>

      <div className="space-y-3">
        {assessment.signals.map((s) => (
          <div key={s.signalName} className="rounded-md border border-slate-200 p-3">
            <div className="flex items-center justify-between">
              <div className="text-sm font-medium">{s.signalName}</div>
              <div className="text-sm text-slate-600">{s.score}</div>
            </div>
            {s.reasons && s.reasons.length > 0 && (
              <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-slate-700">
                {s.reasons.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
