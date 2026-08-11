"""Draws the three boss trophy item textures pixel by pixel in the mod palette."""
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
FRAME = (111, 60, 5, 255)
GOLD = (216, 139, 8, 255)
GOLD_LIGHT = (247, 205, 96, 255)
STONE = (108, 104, 96, 255)
STONE_LIGHT = (166, 162, 150, 255)
BONE = (222, 214, 186, 255)
BLOOD = (138, 42, 32, 255)
EMBER = (232, 108, 42, 255)
VOID = (44, 26, 66, 255)
VOID_LIGHT = (126, 84, 186, 255)
SPARK = (198, 236, 255, 255)


def pedestal(canvas, base, light):
    canvas.rect(8, 25, 23, 29, base)
    canvas.rect(9, 25, 22, 26, light)
    canvas.outline(8, 25, 23, 29, DARK)
    canvas.rect(11, 22, 20, 25, base)
    canvas.outline(11, 22, 20, 25, DARK)


def lesser():
    canvas = Canvas(SIZE)
    pedestal(canvas, STONE, STONE_LIGHT)
    canvas.rect(13, 12, 18, 23, STONE)
    canvas.rect(14, 12, 16, 22, STONE_LIGHT)
    canvas.outline(13, 12, 18, 23, DARK)
    canvas.disc(15, 9, 5, BONE)
    canvas.ring(15, 9, 5, DARK)
    canvas.rect(12, 9, 13, 11, DARK)
    canvas.rect(17, 9, 18, 11, DARK)
    canvas.rect(14, 13, 17, 13, DARK)
    canvas.put(14, 14, DARK)
    canvas.put(16, 14, DARK)
    canvas.rect(9, 4, 11, 5, FRAME)
    canvas.rect(19, 4, 21, 5, FRAME)
    canvas.rect(10, 2, 11, 4, FRAME)
    canvas.rect(19, 2, 20, 4, FRAME)
    canvas.save(os.path.join(ROOT, 'boss_trophy_lesser.png'))


def greater():
    canvas = Canvas(SIZE)
    pedestal(canvas, FRAME, GOLD)
    canvas.rect(13, 13, 18, 23, FRAME)
    canvas.rect(14, 13, 16, 22, GOLD)
    canvas.outline(13, 13, 18, 23, DARK)
    canvas.disc(15, 9, 7, GOLD)
    canvas.disc(15, 9, 5, GOLD_LIGHT)
    canvas.ring(15, 9, 7, DARK)
    canvas.rect(14, 7, 17, 8, BLOOD)
    canvas.rect(13, 10, 18, 11, BLOOD)
    canvas.put(15, 9, EMBER)
    canvas.put(16, 9, EMBER)
    canvas.rect(6, 8, 8, 9, FRAME)
    canvas.rect(23, 8, 25, 9, FRAME)
    canvas.rect(5, 5, 7, 8, GOLD)
    canvas.rect(24, 5, 26, 8, GOLD)
    canvas.outline(5, 5, 7, 8, DARK)
    canvas.outline(24, 5, 26, 8, DARK)
    canvas.save(os.path.join(ROOT, 'boss_trophy_greater.png'))


def legendary():
    canvas = Canvas(SIZE)
    pedestal(canvas, VOID, VOID_LIGHT)
    canvas.rect(13, 14, 18, 23, VOID)
    canvas.rect(14, 14, 16, 22, VOID_LIGHT)
    canvas.outline(13, 14, 18, 23, DARK)
    canvas.disc(15, 9, 8, VOID)
    canvas.ring(15, 9, 8, GOLD)
    canvas.disc(15, 9, 5, VOID_LIGHT)
    canvas.disc(15, 9, 2, SPARK)
    for x, y in ((15, 0), (15, 18), (6, 9), (24, 9)):
        canvas.put(x, y, SPARK)
    canvas.rect(14, 1, 16, 2, GOLD)
    canvas.rect(13, 3, 17, 3, GOLD_LIGHT)
    canvas.rect(4, 11, 6, 12, GOLD)
    canvas.rect(25, 11, 27, 12, GOLD)
    canvas.rect(3, 8, 5, 11, VOID_LIGHT)
    canvas.rect(26, 8, 28, 11, VOID_LIGHT)
    canvas.outline(3, 8, 5, 11, DARK)
    canvas.outline(26, 8, 28, 11, DARK)
    canvas.rect(11, 26, 20, 27, GOLD)
    canvas.save(os.path.join(ROOT, 'boss_trophy_legendary.png'))


if __name__ == '__main__':
    lesser()
    greater()
    legendary()
    print('boss trophy textures written to', os.path.normpath(ROOT))
