"""Draws the generic miniboss token item texture pixel by pixel."""
import math
import os
import struct
import zlib

SIZE = 16
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                    'assets', 'kingdoms', 'textures', 'item')


class Canvas:
    def __init__(self, size):
        self.size = size
        self.pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]

    def put(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.pixels[y][x] = color

    def save(self, path):
        raw = b''
        for row in self.pixels:
            raw += b'\x00' + b''.join(struct.pack('BBBB', *pixel) for pixel in row)

        def chunk(tag, data):
            body = tag + data
            return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body))

        png = b'\x89PNG\r\n\x1a\n'
        png += chunk(b'IHDR', struct.pack('>IIBBBBB', self.size, self.size, 8, 6, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(raw, 9))
        png += chunk(b'IEND', b'')
        with open(path, 'wb') as handle:
            handle.write(png)


EDGE = (58, 38, 8, 255)
DARK = (118, 82, 24, 255)
BASE = (168, 132, 46, 255)
MID = (198, 158, 66, 255)
LIGHT = (224, 188, 100, 255)
SHINE = (250, 232, 170, 255)

CENTER = 7.5
OUTER = 7.2
BORDER = 6.1
RIM = 4.8
GROOVE = 4.0

STAR = (
    (7, 4),
    (6, 5), (7, 5), (8, 5),
    (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6),
    (5, 7), (6, 7), (7, 7), (8, 7), (9, 7),
    (6, 8), (7, 8), (8, 8),
    (5, 9), (9, 9),
)


def build():
    canvas = Canvas(SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            dx = x - CENTER
            dy = y - CENTER
            distance = math.hypot(dx, dy)
            if distance > OUTER:
                continue
            shade = (dx + dy) / 9.0
            if distance > BORDER:
                canvas.put(x, y, EDGE)
            elif distance > RIM:
                if shade < -0.72:
                    canvas.put(x, y, SHINE)
                elif shade < -0.2:
                    canvas.put(x, y, LIGHT)
                elif shade < 0.42:
                    canvas.put(x, y, MID)
                else:
                    canvas.put(x, y, DARK)
            elif distance > GROOVE:
                canvas.put(x, y, DARK)
            else:
                canvas.put(x, y, MID if shade < -0.55 else BASE)

    star = set(STAR)
    for x, y in STAR:
        if (x + 1, y + 1) not in star:
            canvas.put(x + 1, y + 1, DARK)
    for x, y in STAR:
        canvas.put(x, y, SHINE)

    return canvas


def main():
    os.makedirs(ROOT, exist_ok=True)
    build().save(os.path.join(ROOT, 'miniboss_token.png'))


if __name__ == '__main__':
    main()
