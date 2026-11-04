import { ApiStrip } from './components/ApiStrip';
import { EnginePanels } from './components/EnginePanels';
import { Header } from './components/Header';
import { SearchPanel } from './components/SearchPanel';
import { Wall } from './components/Wall';
import { REPOSITORY } from './components/sources';
import { useDemo } from './demo/useDemo';

const RECORDING = `${import.meta.env.BASE_URL}walkthrough`;

export function App() {
  const demo = useDemo();

  if (demo.status === 'failed') {
    return (
      <main className="failed">
        <h1>Document Store</h1>
        <p>The engine didn't load in this browser, so the demo can't run here. It needs a browser with module workers, such as a current version of Chrome, Firefox or Safari. This recording shows it running.</p>
        <picture>
          <source srcSet={`${RECORDING}.png`} media="(prefers-reduced-motion: reduce)" />
          <img src={`${RECORDING}.gif`} width={960} height={600} alt="The demo running: a search for tr, a topic filter, the memory limit dragged down so documents move to disk, then a bulk delete and an undo." />
        </picture>
        <p><a href={REPOSITORY}>Read the source on GitHub</a></p>
      </main>
    );
  }

  return (
    <div className="app">
      <Header demo={demo} />
      <main className="desk">
        <SearchPanel demo={demo} />
        <EnginePanels demo={demo} />
      </main>
      <Wall demo={demo} />
      <ApiStrip demo={demo} />
    </div>
  );
}
