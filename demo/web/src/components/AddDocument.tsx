import { useState, type FormEvent } from 'react';
import type { Demo } from '../demo/useDemo';

export function AddDocument({ demo }: { demo: Demo }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [text, setText] = useState('');
  const [topic, setTopic] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const response = await demo.addDocument(name, text, topic.trim());
    if (response.ok) {
      setName('');
      setText('');
      setTopic('');
      setError(null);
      setOpen(false);
    } else {
      setError(response.error ?? 'The store rejected that document');
    }
  }

  if (!open) {
    return <button type="button" className="quiet add-toggle" onClick={() => setOpen(true)}>Add a document</button>;
  }
  return (
    <form className="add" onSubmit={submit}>
      <label>
        Name
        <input value={name} onChange={event => setName(event.target.value)} placeholder="my-notes" autoComplete="off" spellCheck={false} />
      </label>
      <label>
        Text
        <textarea rows={4} value={text} onChange={event => setText(event.target.value)} />
      </label>
      <label>
        Topic (optional)
        <input value={topic} onChange={event => setTopic(event.target.value)} autoComplete="off" />
      </label>
      {error && <p className="add__error" role="alert">{error}</p>}
      <div className="tour__actions">
        <button type="submit">Add document</button>
        <button type="button" className="quiet" onClick={() => setOpen(false)}>Cancel</button>
      </div>
    </form>
  );
}
