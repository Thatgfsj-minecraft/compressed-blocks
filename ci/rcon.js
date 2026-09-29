// Minimal RCON client (Source RCON protocol) for authoritative server checks
const net = require('net');

function sendPacket(socket, id, type, payload) {
  return new Promise((resolve, reject) => {
    const body = Buffer.alloc(8 + payload.length + 2);
    body.writeInt32LE(id, 0);
    body.writeInt32LE(type, 4);
    body.write(payload, 8, 'ascii');
    body.writeInt8(0, 8 + payload.length);
    body.writeInt8(0, 9 + payload.length);
    const head = Buffer.alloc(4);
    head.writeInt32LE(body.length, 0);
    socket.write(Buffer.concat([head, body]), () => resolve());
  });
}

function readPacket(socket) {
  return new Promise((resolve, reject) => {
    let buffer = Buffer.alloc(0);
    const onData = (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (buffer.length < 4) return;
      const len = buffer.readInt32LE(0);
      if (buffer.length < 4 + len) return;
      const body = buffer.slice(4, 4 + len);
      socket.off('data', onData);
      socket.off('error', onErr);
      resolve({
        id: body.readInt32LE(0),
        type: body.readInt32LE(4),
        payload: body.slice(8, len - 2).toString('utf8'),
      });
    };
    const onErr = (e) => {
      socket.off('data', onData);
      reject(e);
    };
    socket.on('data', onData);
    socket.once('error', onErr);
  });
}

class Rcon {
  connect(port = 25575, host = '127.0.0.1', password = 'testpass') {
    return new Promise((resolve, reject) => {
      this.socket = net.connect(port, host, async () => {
        try {
          await sendPacket(this.socket, 1, 3, password);
          const res = await readPacket(this.socket);
          if (res.id === -1) return reject(new Error('rcon auth failed'));
          resolve(this);
        } catch (e) { reject(e); }
      });
      this.socket.once('error', reject);
    });
  }

  async command(cmd) {
    await sendPacket(this.socket, 7, 2, cmd);
    let payload = '';
    // large responses arrive in multiple packets; drain until a marker
    // response for an empty follow-up command arrives
    const first = await readPacket(this.socket);
    payload += first.payload;
    if (first.payload.length >= 4000) {
      await sendPacket(this.socket, 99, 2, '');
      for (;;) {
        const more = await readPacket(this.socket);
        if (more.id === 99) break;
        payload += more.payload;
      }
    }
    return payload;
  }

  destroy() {
    try { this.socket.destroy(); } catch (e) { /* ignore */ }
  }
}

module.exports = { Rcon };
