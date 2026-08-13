"""Draws the black zone antidote flask item texture pixel by pixel in the mod palette."""
import os
import struct
import zlib

SIZE = 32
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                    'assets', 'kingdoms', 'textures', 'item')


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

    def disc(self, cx, cy, radius, color, y0=None, y1=None):
        top = cy - radius if y0 is None else max(cy - radius, y0)
        bottom = cy + radius if y1 is None else min(cy + radius, y1)
        for y in range(top, bottom + 1):
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


DARK = (18, 14, 24, 255)
GLASS = (96, 108, 128, 255)
GLASS_LIGHT = (176, 190, 208, 255)
CORK = (111, 60, 5, 255)
CORK_LIGHT = (168, 104, 32, 255)
GOLD = (216, 139, 8, 255)
VOID = (34, 24, 46, 255)
LIQUID = (58, 186, 152, 255)
LIQUID_LIGHT = (140, 238, 210, 255)
SPARK = (236, 255, 250, 255)

CENTER_X = 15
CENTER_Y = 21
RADIUS = 9
SURFACE = 18


def antidote():
    canvas = Canvas(SIZE)

    canvas.rect(13, 2, 18, 5, CORK)
    canvas.rect(14, 3, 15, 5, CORK_LIGHT)
    canvas.outline(13, 2, 18, 5, DARK)

    canvas.rect(12, 6, 19, 7, GOLD)
    canvas.outline(12, 6, 19, 7, DARK)

    canvas.rect(13, 8, 18, 13, GLASS)
    canvas.rect(14, 8, 15, 13, GLASS_LIGHT)
    canvas.rect(13, 8, 13, 13, DARK)
    canvas.rect(18, 8, 18, 13, DARK)

    canvas.disc(CENTER_X, CENTER_Y, RADIUS, VOID)
    canvas.disc(CENTER_X, CENTER_Y, RADIUS, LIQUID, y0=SURFACE)
    canvas.disc(CENTER_X, CENTER_Y, RADIUS - 2, LIQUID_LIGHT, y0=SURFACE + 3)
    canvas.rect(CENTER_X - 5, SURFACE, CENTER_X + 5, SURFACE, LIQUID_LIGHT)
    canvas.ring(CENTER_X, CENTER_Y, RADIUS, DARK)

    canvas.put(9, 16, GLASS_LIGHT)
    canvas.put(8, 17, GLASS_LIGHT)
    canvas.put(8, 18, GLASS_LIGHT)
    canvas.put(8, 19, GLASS_LIGHT)
    canvas.put(9, 20, GLASS_LIGHT)

    canvas.rect(14, 20, 16, 22, SPARK)
    canvas.rect(12, 21, 18, 21, SPARK)
    canvas.rect(15, 18, 15, 24, SPARK)
    canvas.put(13, 19, LIQUID_LIGHT)
    canvas.put(17, 19, LIQUID_LIGHT)
    canvas.put(13, 23, LIQUID_LIGHT)
    canvas.put(17, 23, LIQUID_LIGHT)

    canvas.put(6, 8, SPARK)
    canvas.put(25, 12, SPARK)
    canvas.put(24, 6, GOLD)
    canvas.put(7, 12, GOLD)

    canvas.save(os.path.join(ROOT, 'blackzone_antidote.png'))


if __name__ == '__main__':
    antidote()
    print('black zone antidote texture written to', os.path.normpath(ROOT))
