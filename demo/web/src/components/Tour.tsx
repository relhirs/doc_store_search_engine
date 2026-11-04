import type { Demo } from '../demo/useDemo';

export function Tour({ demo }: { demo: Demo }) {
  if (!demo.tourOffered) {
    return null;
  }
  return (
    <aside className="tour" aria-label="Tour">
      <p>A search engine from a Java course project, compiled to JavaScript. Every result, number and movement on this page comes from the engine.</p>
      <div className="tour__actions">
        <button type="button" onClick={() => demo.startTour()}>Take the tour</button>
        <button type="button" className="quiet" onClick={() => demo.setTourOffered(false)}>Explore by yourself</button>
      </div>
    </aside>
  );
}
