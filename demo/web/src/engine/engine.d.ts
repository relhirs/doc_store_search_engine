declare module '@engine' {
  const exports: Record<string, (...args: unknown[]) => string>;
  export = exports;
}
