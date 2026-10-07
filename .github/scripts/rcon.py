"""Minimal RCON client used by the CI client test.

Usage: rcon.py <command>...  (prints each response on its own line)
Connects to 127.0.0.1:$RCON_PORT (default 25575) with $RCON_PASSWORD (default "gctest").
"""
import os
import socket
import struct
import sys

LOGIN, COMMAND = 3, 2


def send(sock, request_id, kind, body):
    payload = body.encode() + b"\x00\x00"
    sock.sendall(struct.pack("<iii", len(payload) + 8, request_id, kind) + payload)


def receive(sock):
    def read_exact(count):
        data = b""
        while len(data) < count:
            chunk = sock.recv(count - len(data))
            if not chunk:
                raise ConnectionError("RCON connection closed")
            data += chunk
        return data

    length = struct.unpack("<i", read_exact(4))[0]
    packet = read_exact(length)
    request_id = struct.unpack("<i", packet[:4])[0]
    return request_id, packet[8:-2].decode(errors="replace")


def main(commands):
    port = int(os.environ.get("RCON_PORT", "25575"))
    with socket.create_connection(("127.0.0.1", port), timeout=30) as sock:
        send(sock, 1, LOGIN, os.environ.get("RCON_PASSWORD", "gctest"))
        if receive(sock)[0] == -1:
            sys.exit("RCON authentication failed")
        for command in commands:
            send(sock, 2, COMMAND, command)
            print(receive(sock)[1])


if __name__ == "__main__":
    main(sys.argv[1:])
