import sys,re
def steps(p):
    t=open(p,'rb').read().decode('utf-8','surrogatepass')
    out=[];cur=None
    for ln in t.split('\n'):
        if ln.startswith('> ') or ln.startswith('# '):
            cur=[ln,[],[],[]];out.append(cur)   # label, reads, writes/deletes, result
        elif cur is None: continue
        else:
            s=ln[2:] if ln.startswith('= ') and ln[2:].startswith('  disk') else ln
            if s.startswith('  disk read'): cur[1].append(s)
            elif s.startswith('  disk'): cur[2].append(s)
            else: cur[3].append(s)
    return out
a,b=steps(sys.argv[1]),steps(sys.argv[2])
print('steps',len(a),len(b))
c=dict(label=0,reads_order=0,reads_set=0,writes_order=0,writes_set=0,result=0)
ex={k:[] for k in c}
for x,y in zip(a,b):
    if x[0]!=y[0]: c['label']+=1; ex['label'].append((x[0],y[0]))
    if x[1]!=y[1]:
        k='reads_order' if sorted(x[1])==sorted(y[1]) else 'reads_set'
        c[k]+=1; ex[k].append((x[0],x[1][:6],y[1][:6]))
    if x[2]!=y[2]:
        k='writes_order' if sorted(x[2])==sorted(y[2]) else 'writes_set'
        c[k]+=1; ex[k].append((x[0],x[2][:6],y[2][:6]))
    if x[3]!=y[3]:
        c['result']+=1; ex['result'].append((x[0],[r[:170] for r in x[3][:3]],[r[:170] for r in y[3][:3]]))
print(c)
for k in ex:
    for e in ex[k][:int(sys.argv[3]) if len(sys.argv)>3 else 3]:
        print(k,e)
