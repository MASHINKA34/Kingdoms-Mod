"""Draws the 18x18 status icons for the three faith blessings, pixel by pixel."""
import os
import struct
import zlib

SIZE = 18
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                    'assets', 'kingdoms', 'textures', 'mob_effect')


class Canvas:
    def __init__(self, size):
        self.size = size
        self.pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]

    def put(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.pixels[y][x] = color

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, color)

    def outline(self, x0, y0, x1, y1, color):
        for x in range(x0, x1 + 1):
            self.put(x, y0, color)
            self.put(x, y1, color)
        for y in range(y0, y1 + 1):
            self.put(x0, y, color)
            self.put(x1, y, color)

    def disc(self, cx, cy, radius, color):
        for y in range(cy - radius, cy + radius + 1):
            for x in range(cx - radius, cx + radius + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    self.put(x, y, color)

    def ring(self, cx, cy, radius, color):
        inner = (radius - 1) * (radius - 1)
        outer = radius * radius
        for y in range(cy - radius, cy + radius + 1):
            for x in range(cx - radius, cx + radius + 1):
                distance = (x - cx) ** 2 + (y - cy) ** 2
                if inner < distance <= outer:
                    self.put(x, y, color)

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


DARK = (38, 21, 3, 255)
GOLD = (216, 139, 8, 255)
GOLD_LIGHT = (247, 205, 96, 255)

SCIENCE_DARK = (26, 86, 122, 255)
SCIENCE = (90, 200, 240, 255)
SCIENCE_LIGHT = (186, 236, 255, 255)

WAR_DARK = (92, 28, 22, 255)
WAR = (200, 70, 60, 255)
WAR_LIGHT = (240, 150, 130, 255)

ECON_DARK = (122, 88, 12, 255)
ECON = (240, 194, 74, 255)
ECON_LIGHT = (255, 232, 160, 255)


def halo(canvas, dark, mid):
    canvas.ring(8, 9, 8, dark)
    canvas.ring(8, 9, 7, mid)


def science():
    canvas = Canvas(SIZE)
    halo(canvas, SCIENCE_DARK, SCIENCE)
    canvas.disc(8, 9, 5, SCIENCE_DARK)
    for y in range(4, 15):
        span = 3 - abs(9 - y) // 3
        canvas.rect(8 - span, y, 8 + span, y, SCIENCE)
    canvas.rect(7, 6, 9, 12, SCIENCE_LIGHT)
    canvas.rect(5, 8, 11, 10, SCIENCE_LIGHT)
    canvas.put(8, 3, SCIENCE_LIGHT)
    canvas.put(8, 15, SCIENCE_LIGHT)
    canvas.disc(8, 9, 1, GOLD_LIGHT)
    canvas.save(os.path.join(ROOT, 'faith_science.png'))


def war():
    canvas = Canvas(SIZE)
    halo(canvas, WAR_DARK, WAR)
    canvas.disc(8, 9, 5, WAR_DARK)
    canvas.rect(7, 3, 9, 14, WAR)
    canvas.rect(4, 6, 12, 8, WAR)
    canvas.rect(7, 3, 8, 13, WAR_LIGHT)
    canvas.rect(4, 6, 11, 7, WAR_LIGHT)
    canvas.rect(6, 12, 10, 13, GOLD)
    canvas.put(8, 2, GOLD_LIGHT)
    canvas.outline(6, 12, 10, 13, DARK)
    canvas.save(os.path.join(ROOT, 'faith_war.png'))


def economy():
    canvas = Canvas(SIZE)
    halo(canvas, ECON_DARK, ECON)
    canvas.disc(8, 9, 5, ECON_DARK)
    canvas.disc(8, 9, 4, ECON)
    canvas.disc(8, 8, 2, ECON_LIGHT)
    canvas.rect(6, 11, 10, 12, GOLD)
    canvas.rect(7, 4, 9, 5, GOLD_LIGHT)
    canvas.put(5, 9, ECON_LIGHT)
    canvas.put(11, 9, ECON_LIGHT)
    canvas.save(os.path.join(ROOT, 'faith_economy.png'))


if __name__ == '__main__':
    os.makedirs(ROOT, exist_ok=True)
    science()
    war()
    economy()
    print('faith effect icons written to', os.path.normpath(ROOT))
