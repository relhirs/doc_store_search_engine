import sys,hashlib,collections,os
def canon(p):
    lines=open(p,'rb').read().split(b'\n'); out=[]; reads=[]
    for ln in lines:
        if ln.startswith(b'  disk read'): reads.append(ln); continue
        out.extend(sorted(reads)); reads=[]; out.append(ln)
    return b'\n'.join(out)
a,b=canon('jvm-disk.txt'),canon('teavm.txt')
print('canonical sha jvm-disk',hashlib.sha256(a).hexdigest()[:16],'teavm',hashlib.sha256(b).hexdigest()[:16],'EQUAL' if a==b else 'DIFFERENT')
# which steps still differ in raw read order, and is the tail (getResults) read order the same
def steps(p):
    out=[];cur=None
    for ln in open(p,'rb').read().decode('utf-8','replace').split('\n'):
        if ln.startswith('> '): cur=[ln,[]]; out.append(cur)
        elif ln.startswith('  disk read') and cur: cur[1].append(ln)
    return out
c=collections.Counter()
for x,y in zip(steps('jvm-disk.txt'),steps('teavm.txt')):
    if x[1]!=y[1]: c[x[0].split(' ')[1]]+=1
print('raw read-order differences by operation:',dict(c))
sys.exit(0 if a==b else 1)
