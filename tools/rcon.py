"""Minimal RCON client used to script the local dev server (see tools/README notes)."""

import socket
import struct
import sys
import time


def pack(request_id: int, packet_type: int, body: str) -> bytes:
    payload = struct.pack("<ii", request_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read_packet(sock: socket.socket):
    raw_length = sock.recv(4)
    if len(raw_length) < 4:
        return None
    length = struct.unpack("<i", raw_length)[0]
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            return None
        data += chunk
    request_id, packet_type = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", errors="replace")
    return request_id, packet_type, body


def main() -> int:
    commands = sys.argv[3:]
    sock = socket.create_connection(("127.0.0.1", int(sys.argv[1])), timeout=10)
    sock.sendall(pack(1, 3, sys.argv[2]))
    time.sleep(0.3)
    auth = read_packet(sock)
    if auth is None or auth[0] == -1:
        print("authentication failed")
        return 1

    for index, command in enumerate(commands, start=2):
        sock.sendall(pack(index, 2, command))
        time.sleep(0.3)
        response = read_packet(sock)
        print(f"> {command}")
        if response is not None:
            print(response[2].strip())
    sock.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
