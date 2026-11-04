const m = await import(process.argv[2]);
m.reset();
try { m.undo(); console.log('undo on empty store: no error'); }
catch (e) { console.log('undo on empty store caught:', e?.constructor?.name, '|', String(e).slice(0, 80)); }
try { console.log('after error, put returns', m.putText('http://x/a', 'tree trie')); console.log('search', m.searchPrefix('tr')); }
catch (e) { console.log('STORE BROKEN AFTER ERROR', String(e).slice(0, 100)); }
try { m.putText('http://x/bad uri', 'text'); console.log('bad uri: no error'); }
catch (e) { console.log('bad uri caught:', e?.constructor?.name, '|', String(e).slice(0, 80)); }
console.log('still works:', m.searchPrefix('tr'));
