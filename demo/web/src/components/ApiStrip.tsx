import { formatTime } from '../demo/format';
import type { Demo } from '../demo/useDemo';

export function ApiStrip({ demo }: { demo: Demo }) {
  const { last, state } = demo;
  const time = formatTime(last?.nanos);
  const details = [last?.summary, time].filter(Boolean).join(', ');
  return (
    <footer className="strip" data-tour="strip">
      <p className="strip__line" role="status">
        {last && <code>{last.call}</code>}
        {details && <span className={last?.ok ? undefined : 'strip__error'}>{details}</span>}
      </p>
      <button type="button" data-tour="undo" onClick={() => demo.undo()} disabled={!state?.history.length}>Undo</button>
    </footer>
  );
}
