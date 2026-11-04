import type { Response } from './types';

interface Waiting {
  resolve: (response: Response<never>) => void;
}

interface Lane {
  busy: boolean;
  next: (() => void) | null;
  drop: (() => void) | null;
}

/** Talks to the engine in its worker. Calls run in the order they're made. */
export class EngineClient {
  private readonly worker: Worker;
  private readonly waiting = new Map<number, Waiting>();
  private readonly lanes = new Map<string, Lane>();
  private nextId = 0;
  readonly ready: Promise<void>;

  constructor() {
    this.worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' });
    this.ready = new Promise((resolve, reject) => {
      this.worker.onerror = event => reject(new Error(event.message || 'The engine failed to load'));
      this.worker.onmessage = ({ data }) => {
        if (data.ready) {
          resolve();
          return;
        }
        const waiting = this.waiting.get(data.id);
        this.waiting.delete(data.id);
        waiting?.resolve(JSON.parse(data.json));
      };
    });
  }

  call<Result = unknown>(op: string, ...args: unknown[]): Promise<Response<Result>> {
    const id = this.nextId++;
    return new Promise(resolve => {
      this.waiting.set(id, { resolve: resolve as Waiting['resolve'] });
      this.worker.postMessage({ id, op, args });
    });
  }

  /**
   * Holds one call in flight per lane and keeps only the newest behind it, so fast input skips to the
   * latest value instead of queueing. A call that gets skipped resolves to null.
   */
  latest<Result = unknown>(laneName: string, op: string, ...args: unknown[]): Promise<Response<Result> | null> {
    const lane = this.lanes.get(laneName) ?? { busy: false, next: null, drop: null };
    this.lanes.set(laneName, lane);
    return new Promise(resolve => {
      const send = () => {
        lane.busy = true;
        this.call<Result>(op, ...args).then(response => {
          lane.busy = false;
          resolve(response);
          const next = lane.next;
          lane.next = null;
          lane.drop = null;
          next?.();
        });
      };
      if (lane.busy) {
        lane.drop?.();
        lane.next = send;
        lane.drop = () => resolve(null);
      } else {
        send();
      }
    });
  }
}
