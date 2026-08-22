"""Draws the nether portal anchor block and hellfire igniter textures pixel by pixel."""
import os
import struct
import zlib

SIZE = 16
HERE = os.path.dirname(__file__)
BLOCK_ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'kingdoms', 'textures', 'block')
ITEM_ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'kingdoms', 'textures', 'item')
ART_ROOT = os.path.join(HERE, '..', 'art', 'aseprite', 'nether_portal')
PREVIEW_ROOT = os.path.join(ART_ROOT, 'previews')
PREVIEW_SCALE = 8


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

    def ellipse(self, cx, cy, rx, ry, color):
        for y in range(self.size):
            for x in range(self.size):
                dx = (x + 0.5 - cx) / rx
                dy = (y + 0.5 - cy) / ry
                if dx * dx + dy * dy <= 1.0:
                    self.put(x, y, color)

    def stamp(self, rows, palette):
        for y, row in enumerate(rows):
            if len(row) != self.size:
                raise ValueError('row %d has %d columns' % (y, len(row)))
            for x, key in enumerate(row):
                if key != '.':
                    self.put(x, y, palette[key])

    def scaled(self, factor):
        canvas = Canvas(self.size * factor)
        for y in range(canvas.size):
            for x in range(canvas.size):
                canvas.pixels[y][x] = self.pixels[y // factor][x // factor]
        return canvas

    def save_png(self, path):
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

    def save_aseprite(self, path, layer_name):
        raw = b''
        for row in self.pixels:
            raw += b''.join(struct.pack('BBBB', *pixel) for pixel in row)

        def chunk(tag, data):
            return struct.pack('<IH', len(data) + 6, tag) + data

        layer = struct.pack('<HHHHHHB3x', 3, 0, 0, 0, 0, 0, 255)
        layer += struct.pack('<H', len(layer_name)) + layer_name.encode('utf-8')
        cel = struct.pack('<HhhBHh5x', 0, 0, 0, 255, 2, 0)
        cel += struct.pack('<HH', self.size, self.size) + zlib.compress(raw, 9)
        chunks = chunk(0x2004, layer) + chunk(0x2005, cel)
        frame = struct.pack('<IHHH2xI', len(chunks) + 16, 0xF1FA, 2, 100, 2) + chunks
        header = struct.pack(
            '<IHHHHHIH8xB3xHBBhhHH84x',
            len(frame) + 128, 0xA5E0, 1, self.size, self.size, 32, 1, 100, 0, 0, 1, 1, 0, 0, 16, 16
        )
        with open(path, 'wb') as handle:
            handle.write(header + frame)


EDGE = (10, 7, 16, 255)
OBSIDIAN_DARK = (20, 14, 30, 255)
OBSIDIAN = (30, 21, 44, 255)
OBSIDIAN_LIGHT = (44, 32, 62, 255)
OBSIDIAN_HIGH = (60, 46, 84, 255)
GOLD_DARK = (122, 88, 34, 255)
GOLD = (186, 142, 60, 255)
GOLD_LIGHT = (232, 194, 106, 255)
CRIMSON_DARK = (86, 16, 20, 255)
CRIMSON = (150, 30, 28, 255)
EMBER = (214, 76, 30, 255)
EMBER_LIGHT = (248, 148, 58, 255)
CORE = (255, 226, 156, 255)
IRON_DARK = (58, 58, 70, 255)
IRON = (104, 104, 120, 255)
IRON_LIGHT = (158, 158, 176, 255)

ITEM_PALETTE = {
    'K': EDGE,
    'i': IRON_DARK,
    'I': IRON,
    'W': IRON_LIGHT,
    'g': GOLD_DARK,
    'G': GOLD,
    'Y': GOLD_LIGHT,
    'r': CRIMSON_DARK,
    'R': CRIMSON,
    'e': EMBER,
    'E': EMBER_LIGHT,
    'C': CORE,
}

SPECKLE_DARK = (
    (3, 5), (4, 9), (6, 2), (9, 3), (11, 6), (12, 10), (5, 12), (10, 13), (2, 8), (13, 4),
)
SPECKLE_LIGHT = (
    (4, 3), (7, 2), (11, 4), (3, 11), (12, 7), (8, 12), (2, 6), (13, 9), (6, 13), (9, 10),
)

IGNITER = (
    '................',
    '...........CC...',
    '..........CEEC..',
    '.........CEeeEC.',
    '.........EeRReC.',
    '.........KeReK..',
    '........KWIiK...',
    '.......KWIiK....',
    '......KGYGK.....',
    '.....KGYGK......',
    '....KGYGK.......',
    '...KGYGK........',
    '.KrRRrK.........',
    '.KReCeRK........',
    '.KRerRK.........',
    '..KRRK..........',
)


def obsidian_field(canvas):
    canvas.rect(0, 0, 15, 15, OBSIDIAN)
    for x, y in SPECKLE_DARK:
        canvas.put(x, y, OBSIDIAN_DARK)
    for x, y in SPECKLE_LIGHT:
        canvas.put(x, y, OBSIDIAN_LIGHT)
    canvas.outline(0, 0, 15, 15, EDGE)
    canvas.outline(1, 1, 14, 14, GOLD_DARK)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        canvas.put(x, y, GOLD)
    for x in (5, 6, 9, 10):
        canvas.put(x, 1, GOLD)
        canvas.put(x, 14, GOLD)
    for y in (5, 6, 9, 10):
        canvas.put(1, y, GOLD)
        canvas.put(14, y, GOLD)


def rivets(canvas):
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        canvas.put(x, y, GOLD_DARK)
    canvas.put(3, 3, GOLD)
    canvas.put(12, 12, GOLD)


def anchor_side():
    canvas = Canvas(SIZE)
    obsidian_field(canvas)
    rivets(canvas)
    canvas.ellipse(8.0, 8.0, 3.4, 4.8, CRIMSON_DARK)
    canvas.ellipse(8.0, 8.0, 2.6, 3.9, CRIMSON)
    canvas.ellipse(8.0, 8.0, 1.9, 3.0, EMBER)
    canvas.ellipse(8.0, 8.0, 1.2, 2.0, EMBER_LIGHT)
    canvas.ellipse(8.0, 7.6, 0.6, 1.1, CORE)
    canvas.put(7, 3, GOLD_LIGHT)
    canvas.put(8, 3, GOLD_LIGHT)
    canvas.put(7, 12, GOLD_LIGHT)
    canvas.put(8, 12, GOLD_LIGHT)
    return canvas


def anchor_top():
    canvas = Canvas(SIZE)
    obsidian_field(canvas)
    rivets(canvas)
    canvas.ellipse(8.0, 8.0, 5.4, 5.4, GOLD_DARK)
    canvas.ellipse(8.0, 8.0, 4.6, 4.6, OBSIDIAN_DARK)
    canvas.ellipse(8.0, 8.0, 3.9, 3.9, OBSIDIAN_HIGH)
    canvas.ellipse(8.0, 8.0, 3.2, 3.2, CRIMSON_DARK)
    canvas.ellipse(8.0, 8.0, 2.5, 2.5, CRIMSON)
    canvas.ellipse(8.0, 8.0, 1.8, 1.8, EMBER)
    canvas.ellipse(8.0, 8.0, 1.1, 1.1, EMBER_LIGHT)
    canvas.ellipse(8.0, 8.0, 0.5, 0.5, CORE)
    for x, y in ((7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)):
        canvas.put(x, y, GOLD_LIGHT)
    return canvas


def anchor_bottom():
    canvas = Canvas(SIZE)
    obsidian_field(canvas)
    rivets(canvas)
    canvas.rect(6, 6, 9, 9, OBSIDIAN_DARK)
    canvas.outline(6, 6, 9, 9, GOLD_DARK)
    canvas.put(7, 7, CRIMSON_DARK)
    canvas.put(8, 8, CRIMSON_DARK)
    return canvas


def igniter():
    canvas = Canvas(SIZE)
    canvas.stamp(IGNITER, ITEM_PALETTE)
    return canvas


TEXTURES = (
    ('nether_portal_anchor_side', BLOCK_ROOT, anchor_side),
    ('nether_portal_anchor_top', BLOCK_ROOT, anchor_top),
    ('nether_portal_anchor_bottom', BLOCK_ROOT, anchor_bottom),
    ('nether_igniter', ITEM_ROOT, igniter),
)


def main():
    os.makedirs(PREVIEW_ROOT, exist_ok=True)
    for name, root, factory in TEXTURES:
        canvas = factory()
        canvas.save_png(os.path.join(root, name + '.png'))
        canvas.save_aseprite(os.path.join(ART_ROOT, name + '.aseprite'), name)
        canvas.scaled(PREVIEW_SCALE).save_png(
            os.path.join(PREVIEW_ROOT, '%s_%dx.png' % (name, PREVIEW_SCALE))
        )
        print('wrote', name)


if __name__ == '__main__':
    main()
