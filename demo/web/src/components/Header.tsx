import type { Demo } from '../demo/useDemo';
import { REPOSITORY } from './sources';

export function Header({ demo }: { demo: Demo }) {
  return (
    <header className="masthead">
      <h1>Document Store</h1>
      <p>Real Java, running in your browser</p>
      <nav aria-label="Demo">
        <button type="button" className="quiet" onClick={() => { demo.setTour({ step: 0, task: 0 }); demo.setTourOffered(false); }}>Tour</button>
        <a className="quiet" href={REPOSITORY}>Source</a>
        <button type="button" className="quiet" onClick={() => demo.reset()}>Reset</button>
      </nav>
    </header>
  );
}
