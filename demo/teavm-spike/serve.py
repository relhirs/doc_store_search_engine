import http.server,sys,os,functools
root,dest=sys.argv[1],sys.argv[2]
class H(http.server.SimpleHTTPRequestHandler):
    def __init__(s,*a,**k): super().__init__(*a,directory=root,**k)
    def log_message(s,*a): pass
    def do_POST(s):
        body=s.rfile.read(int(s.headers['content-length']))
        open(dest,'wb').write(body); s.send_response(204); s.end_headers()
srv=http.server.ThreadingHTTPServer(('127.0.0.1',0),H)
print(srv.server_address[1],flush=True)
srv.serve_forever()
