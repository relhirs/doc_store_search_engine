import { TOUR } from '../demo/tour';
import type { Demo } from '../demo/useDemo';

export function Tour({ demo }: { demo: Demo }) {
  const { tour, tourOffered } = demo;

  if (tourOffered) {
    return (
      <aside className="tour" aria-label="Tour">
        <p>A search engine from a Java course project, compiled to JavaScript. Every result, number and movement on this page comes from the engine.</p>
        <div className="tour__actions">
          <button type="button" onClick={() => { demo.setTour({ step: 0, task: 0 }); demo.setTourOffered(false); }}>Take the four-step tour</button>
          <button type="button" className="quiet" onClick={() => demo.setTourOffered(false)}>Explore by yourself</button>
        </div>
      </aside>
    );
  }
  if (!tour) {
    return null;
  }
  if (tour.step >= TOUR.length) {
    return (
      <aside className="tour" aria-label="Tour">
        <p>That's the tour. Keep going: add a document, set a tight limit, or press Reset to start over.</p>
        <div className="tour__actions">
          <button type="button" className="quiet" onClick={() => demo.setTour(null)}>Close</button>
        </div>
      </aside>
    );
  }
  const step = TOUR[tour.step];
  return (
    <aside className="tour" aria-label="Tour" aria-live="polite">
      <h2>Step {tour.step + 1} of {TOUR.length}: {step.title}</h2>
      <p>{step.tasks[tour.task].text}</p>
      <div className="tour__actions">
        <button type="button" className="quiet" onClick={() => demo.setTour(null)}>End tour</button>
      </div>
    </aside>
  );
}
