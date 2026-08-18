"""Draws the research bench block textures pixel by pixel."""
import os
import struct
import zlib

SIZE = 16
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                    'assets', 'kingdoms', 'textures', 'block')


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
        for y in range(self.size):
            for x in range(self.size):
                if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
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


WOOD = (63, 45, 38, 255)
WOOD_DARK = (42, 30, 25, 255)
WOOD_LIGHT = (86, 63, 52, 255)
WOOD_EDGE = (28, 20, 17, 255)
GOLD = (168, 132, 46, 255)
GOLD_LIGHT = (224, 188, 100, 255)
GOLD_DARK = (110, 84, 28, 255)
BRASS = (146, 112, 54, 255)
BRASS_LIGHT = (196, 158, 84, 255)
SCIENCE_DARK = (26, 86, 122, 255)
SCIENCE = (90, 200, 240, 255)
SCIENCE_LIGHT = (186, 236, 255, 255)
PAPER = (206, 197, 168, 255)
PAPER_DARK = (168, 158, 130, 255)
INK = (36, 48, 62, 255)
IRON = (116, 116, 126, 255)
IRON_DARK = (72, 72, 82, 255)


def planks(canvas, base, dark, light):
    canvas.rect(0, 0, 15, 15, base)
    for y in (3, 7, 11, 15):
        for x in range(16):
            canvas.put(x, y, dark)
    for y in (0, 4, 8, 12):
        for x in range(16):
            canvas.put(x, y, light)
    for x, y in ((2, 1), (9, 2), (5, 6), (13, 5), (3, 9), (11, 10), (7, 13), (14, 14)):
        canvas.put(x, y, dark)


def top():
    canvas = Canvas(SIZE)
    planks(canvas, WOOD, WOOD_DARK, WOOD_LIGHT)
    canvas.outline(0, 0, 15, 15, WOOD_EDGE)
    canvas.outline(1, 1, 14, 14, GOLD_DARK)
    canvas.put(1, 1, GOLD)
    canvas.put(14, 1, GOLD)
    canvas.put(1, 14, GOLD)
    canvas.put(14, 14, GOLD)

    canvas.rect(3, 3, 9, 12, PAPER_DARK)
    canvas.rect(3, 3, 8, 11, PAPER)
    for y in (5, 7, 9):
        for x in range(4, 8):
            canvas.put(x, y, INK)
    canvas.rect(4, 3, 7, 4, SCIENCE_DARK)
    canvas.put(5, 4, SCIENCE)
    canvas.put(6, 3, SCIENCE)
    canvas.put(4, 11, INK)
    canvas.put(6, 11, INK)

    canvas.rect(11, 3, 13, 5, BRASS)
    canvas.put(11, 3, BRASS_LIGHT)
    canvas.put(12, 4, GOLD_LIGHT)
    canvas.rect(11, 7, 13, 12, IRON_DARK)
    canvas.rect(11, 8, 13, 11, SCIENCE_DARK)
    canvas.rect(12, 9, 12, 11, SCIENCE)
    canvas.put(12, 8, SCIENCE_LIGHT)
    return canvas


def side():
    canvas = Canvas(SIZE)
    planks(canvas, WOOD, WOOD_DARK, WOOD_LIGHT)
    canvas.outline(0, 0, 15, 15, WOOD_EDGE)
    canvas.rect(0, 0, 15, 1, WOOD_LIGHT)
    canvas.rect(0, 1, 15, 1, GOLD_DARK)
    canvas.put(2, 1, GOLD)
    canvas.put(13, 1, GOLD)

    canvas.rect(2, 3, 13, 7, WOOD_DARK)
    canvas.outline(2, 3, 13, 7, WOOD_EDGE)
    canvas.rect(6, 5, 9, 5, BRASS)
    canvas.put(6, 5, BRASS_LIGHT)

    canvas.rect(2, 9, 13, 13, WOOD_DARK)
    canvas.outline(2, 9, 13, 13, WOOD_EDGE)
    canvas.rect(4, 11, 6, 11, SCIENCE_DARK)
    canvas.put(5, 11, SCIENCE)
    canvas.rect(9, 11, 11, 11, BRASS)
    canvas.put(11, 11, BRASS_LIGHT)

    canvas.rect(0, 14, 15, 15, WOOD_DARK)
    canvas.put(1, 14, IRON)
    canvas.put(14, 14, IRON)
    return canvas


def bottom():
    canvas = Canvas(SIZE)
    planks(canvas, WOOD_DARK, WOOD_EDGE, WOOD)
    canvas.outline(0, 0, 15, 15, WOOD_EDGE)
    canvas.rect(1, 1, 3, 3, IRON_DARK)
    canvas.rect(12, 1, 14, 3, IRON_DARK)
    canvas.rect(1, 12, 3, 14, IRON_DARK)
    canvas.rect(12, 12, 14, 14, IRON_DARK)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        canvas.put(x, y, IRON)
    return canvas


def main():
    os.makedirs(ROOT, exist_ok=True)
    top().save(os.path.join(ROOT, 'research_bench_top.png'))
    side().save(os.path.join(ROOT, 'research_bench_side.png'))
    bottom().save(os.path.join(ROOT, 'research_bench_bottom.png'))


if __name__ == '__main__':
    main()
