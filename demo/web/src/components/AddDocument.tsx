import { useState, type ChangeEvent, type FormEvent } from 'react';
import type { Demo } from '../demo/useDemo';

// A name becomes a URI, so the engine only takes lowercase letters, digits and dashes
const toStoredName = (name: string) => name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

export function AddDocument({ demo }: { demo: Demo }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [text, setText] = useState('');
  const [topic, setTopic] = useState('');
  const [error, setError] = useState<string | null>(null);
  const stored = toStoredName(name);

  function edit(set: (value: string) => void) {
    return (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      set(event.target.value);
      setError(null);
    };
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const response = await demo.addDocument(stored, text, topic.trim());
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
        <input value={name} onChange={edit(setName)} placeholder="my-notes" autoComplete="off" spellCheck={false} />
      </label>
      {name.trim() && stored !== name && (
        <p className="add__hint">{stored ? <>Saved as <code>{stored}</code></> : 'Use at least one letter from a to z or digit'}</p>
      )}
      <label>
        Text
        <textarea rows={4} value={text} onChange={edit(setText)} />
      </label>
      <label>
        Topic (optional)
        <input value={topic} onChange={edit(setTopic)} autoComplete="off" />
      </label>
      {error && <p className="add__error" role="alert">{error}</p>}
      <div className="tour__actions">
        <button type="submit">Add document</button>
        <button type="button" className="quiet" onClick={() => setOpen(false)}>Cancel</button>
      </div>
    </form>
  );
}
