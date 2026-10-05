# RCON 命令注入工具（headless 服务端实机验证用，配合 runServer 的 stdin stop 关服纪律）
# 前提：server.properties 里 enable-rcon=true、rcon.port=25575、rcon.password=cbport-test
# 用法：python rcon_26.3.py "命令1" "命令2" ...（命令输出打在 stdout；say/聊天标记要从 run/logs/latest.log 读）
import socket, struct, sys

def pkt(rid, ptype, body):
    data = struct.pack('<ii', rid, ptype) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(data)) + data

def read_pkt(s):
    ln = struct.unpack('<i', s.recv(4))[0]
    data = b''
    while len(data) < ln:
        chunk = s.recv(ln - len(data))
        if not chunk:
            break
        data += chunk
    ptype, body = struct.unpack('<ii', data[:8])[1], data[8:-2]
    return ptype, body.decode('utf-8', 'replace')

s = socket.create_connection(('127.0.0.1', 25575), timeout=15)
s.sendall(pkt(1, 3, 'cbport-test'))
t, body = read_pkt(s)
print(f'# auth ptype={t} body={body!r}')
for i, cmd in enumerate(sys.argv[1:], start=2):
    s.sendall(pkt(i, 2, cmd))
    t, body = read_pkt(s)
    print(f'>>> {cmd}\n{body}')
s.close()
