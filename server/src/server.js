import http from "node:http";
import { WebSocketServer, WebSocket } from "ws";

const port = Number.parseInt(process.env.PORT ?? "8787", 10);
const rooms = new Map();
const maximumRoomMembers = 32;
const maximumFrameBytes = 24 * 1024;
const maximumEventsPerWindow = 40;
const rateWindowMilliseconds = 10_000;

const server = http.createServer((request, response) => {
  if (request.url === "/health") {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(JSON.stringify({ status: "ok" }));
    return;
  }
  response.writeHead(404).end();
});

const sockets = new WebSocketServer({ noServer: true, maxPayload: maximumFrameBytes });

server.on("upgrade", (request, socket, head) => {
  if (request.url !== "/v1/relay") {
    socket.destroy();
    return;
  }
  sockets.handleUpgrade(request, socket, head, (webSocket) => {
    sockets.emit("connection", webSocket, request);
  });
});

sockets.on("connection", (socket) => {
  const member = {
    channel: null,
    sender: null,
    rateWindowStartedAt: Date.now(),
    eventsInWindow: 0,
  };
  const joinDeadline = setTimeout(() => socket.close(1008, "join required"), 10_000);

  socket.on("message", (buffer) => {
    if (buffer.byteLength > maximumFrameBytes || !acceptRate(member)) {
      socket.close(1008, "rate or size limit");
      return;
    }
    let message;
    try {
      message = JSON.parse(buffer.toString("utf8"));
    } catch {
      socket.close(1007, "invalid json");
      return;
    }

    if (message.type === "join") {
      if (member.channel !== null || !validChannel(message.channel) || !validSender(message.sender)) {
        socket.close(1008, "invalid join");
        return;
      }
      const room = rooms.get(message.channel) ?? new Set();
      if (room.size >= maximumRoomMembers) {
        socket.close(1013, "room full");
        return;
      }
      member.channel = message.channel;
      member.sender = message.sender;
      room.add(socket);
      rooms.set(message.channel, room);
      clearTimeout(joinDeadline);
      return;
    }

    if (
      message.type !== "event" ||
      member.channel === null ||
      message.channel !== member.channel ||
      message.sender !== member.sender ||
      typeof message.payload !== "string" ||
      message.payload.length > maximumFrameBytes
    ) {
      socket.close(1008, "invalid event");
      return;
    }

    const room = rooms.get(member.channel);
    if (room === undefined) return;
    const outbound = JSON.stringify({
      type: "event",
      channel: member.channel,
      sender: member.sender,
      payload: message.payload,
    });
    for (const peer of room) {
      if (peer !== socket && peer.readyState === WebSocket.OPEN) peer.send(outbound);
    }
  });

  socket.on("close", () => {
    clearTimeout(joinDeadline);
    if (member.channel === null) return;
    const room = rooms.get(member.channel);
    if (room === undefined) return;
    room.delete(socket);
    if (room.size === 0) rooms.delete(member.channel);
  });
});

function acceptRate(member) {
  const now = Date.now();
  if (now - member.rateWindowStartedAt >= rateWindowMilliseconds) {
    member.rateWindowStartedAt = now;
    member.eventsInWindow = 0;
  }
  member.eventsInWindow += 1;
  return member.eventsInWindow <= maximumEventsPerWindow;
}

function validChannel(value) {
  return typeof value === "string" && /^[A-Za-z0-9_-]{43}$/.test(value);
}

function validSender(value) {
  return typeof value === "string" && /^[A-Za-z0-9_-]{8,80}$/.test(value);
}

server.listen(port, "0.0.0.0", () => {
  process.stdout.write(`Boyiso relay listening on ${port}\n`);
});
