"""Minimal Source RCON client for the local fabric test server."""
import socket
import struct
import sys

HOST = "127.0.0.1"
PORT = 25577
PASS = "testpass"


def _send(sock, req_id, ptype, payload):
    data = struct.pack("<ii", req_id, ptype) + payload.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)


def _recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("socket closed")
        buf += chunk
    return buf


def _recv_packet(sock):
    (size,) = struct.unpack("<i", _recv_exact(sock, 4))
    data = _recv_exact(sock, size)
    req_id, ptype = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", "replace")
    return req_id, ptype, body


def connect():
    sock = socket.create_connection((HOST, PORT), timeout=10)
    _send(sock, 1, 3, PASS)
    rid, ptype, body = _recv_packet(sock)
    if rid == -1:
        raise PermissionError("rcon auth failed")
    return sock


def command(sock, cmd):
    _send(sock, 2, 2, cmd)
    rid, ptype, body = _recv_packet(sock)
    # A command may be followed by extra fragments; keep reading until we
    # receive a response with our id (type 2) that terminates the exchange.
    return body


def main():
    sock = connect()
    try:
        for cmd in sys.argv[1:]:
            out = command(sock, cmd)
            print(f"> {cmd}\n{out}")
    finally:
        sock.close()


if __name__ == "__main__":
    main()
