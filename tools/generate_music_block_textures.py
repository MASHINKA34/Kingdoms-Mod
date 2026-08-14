"""Draws the music speaker block textures pixel by pixel."""
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

    def ring(self, cx, cy, radius, thickness, color):
        inner = (radius - thickness) ** 2
        outer = radius * radius
        for y in range(self.size):
            for x in range(self.size):
                distance = (x - cx) ** 2 + (y - cy) ** 2
                if inner <= distance <= outer:
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


CASE = (43, 46, 56, 255)
CASE_DARK = (28, 30, 38, 255)
CASE_LIGHT = (60, 64, 78, 255)
CASE_EDGE = (20, 21, 27, 255)
GOLD = (201, 162, 76, 255)
GOLD_LIGHT = (246, 230, 197, 255)
GOLD_DARK = (140, 110, 46, 255)
GRILLE = (24, 25, 32, 255)
GRILLE_LIGHT = (52, 55, 66, 255)
CONE = (72, 60, 44, 255)
CONE_DARK = (46, 38, 28, 255)


def frame(canvas):
    canvas.rect(0, 0, 15, 15, CASE)
    canvas.outline(0, 0, 15, 15, CASE_EDGE)
    canvas.outline(1, 1, 14, 14, CASE_LIGHT)
    for x in range(2, 14):
        canvas.put(x, 13, CASE_DARK)
    for y in range(2, 14):
        canvas.put(13, y, CASE_DARK)


def side():
    canvas = Canvas(SIZE)
    frame(canvas)
    canvas.rect(2, 2, 13, 12, GRILLE)
    for y in range(2, 13):
        for x in range(2, 14):
            if (x + y) % 2 == 0:
                canvas.put(x, y, GRILLE_LIGHT)
    canvas.disc(7, 7, 4, CONE_DARK)
    canvas.disc(7, 7, 3, CONE)
    canvas.ring(7, 7, 4, 1, CASE_EDGE)
    canvas.disc(7, 7, 1, GOLD_DARK)
    canvas.put(7, 6, GOLD)
    canvas.put(6, 7, GOLD)
    canvas.rect(2, 13, 13, 14, CASE_DARK)
    canvas.rect(3, 13, 5, 13, GOLD_DARK)
    canvas.put(3, 13, GOLD)
    return canvas


def top():
    canvas = Canvas(SIZE)
    frame(canvas)
    canvas.rect(2, 2, 13, 13, CASE_DARK)
    canvas.outline(2, 2, 13, 13, CASE_LIGHT)
    canvas.rect(4, 4, 11, 6, GRILLE)
    for x in range(4, 12):
        if x % 2 == 0:
            canvas.put(x, 5, GOLD_DARK)
    canvas.rect(4, 9, 11, 11, GRILLE)
    canvas.put(5, 10, GOLD)
    canvas.put(7, 10, GOLD_LIGHT)
    canvas.put(9, 10, GOLD)
    canvas.put(3, 3, GOLD_DARK)
    canvas.put(12, 3, GOLD_DARK)
    canvas.put(3, 12, GOLD_DARK)
    canvas.put(12, 12, GOLD_DARK)
    return canvas


def bottom():
    canvas = Canvas(SIZE)
    frame(canvas)
    canvas.rect(2, 2, 13, 13, CASE_DARK)
    canvas.rect(3, 3, 4, 4, CASE_EDGE)
    canvas.rect(11, 3, 12, 4, CASE_EDGE)
    canvas.rect(3, 11, 4, 12, CASE_EDGE)
    canvas.rect(11, 11, 12, 12, CASE_EDGE)
    for x in range(6, 10):
        canvas.put(x, 7, CASE)
        canvas.put(x, 8, CASE)
    return canvas


def main():
    os.makedirs(ROOT, exist_ok=True)
    side().save(os.path.join(ROOT, 'music_block_side.png'))
    top().save(os.path.join(ROOT, 'music_block_top.png'))
    bottom().save(os.path.join(ROOT, 'music_block_bottom.png'))


if __name__ == '__main__':
    main()
