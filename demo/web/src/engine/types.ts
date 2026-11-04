export interface Tile {
  name: string;
  bytes: number;
  metadata: Record<string, string>;
}

export interface HistoryEntry {
  label: string;
  bulk: boolean;
  targets: string[];
}

export interface EngineState {
  ram: Tile[];
  disk: Tile[];
  nextToEvict: string | null;
  ramCount: number;
  ramBytes: number;
  maxDocuments: number;
  maxBytes: number;
  history: HistoryEntry[];
}

export interface TierChange {
  name: string;
  tier: 'ram' | 'disk';
}

export interface Events {
  evicted: string[];
  loaded: string[];
  added: TierChange[];
  deleted: TierChange[];
}

export interface Row {
  name: string;
  count?: number;
  text: string;
  metadata: Record<string, string>;
}

export interface IndexLevel {
  prefix: string;
  next: string[];
  documents: number;
}

export interface Response<Result = unknown> {
  ok: boolean;
  call?: string | null;
  storeNanos?: number | null;
  result?: Result;
  error?: string;
  events?: Events;
  state: EngineState;
}
