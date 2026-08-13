"""Draws the dungeon core and dungeon chest block textures pixel by pixel."""
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


STONE = (46, 41, 57, 255)
STONE_DARK = (32, 28, 40, 255)
STONE_LIGHT = (62, 55, 78, 255)
STONE_EDGE = (23, 20, 30, 255)
GOLD = (168, 132, 46, 255)
GOLD_LIGHT = (224, 188, 100, 255)
PURPLE_DEEP = (84, 28, 134, 255)
PURPLE = (155, 48, 255, 255)
PURPLE_LIGHT = (200, 142, 255, 255)
SPARK = (242, 228, 255, 255)

WOOD = (63, 45, 38, 255)
WOOD_DARK = (42, 30, 25, 255)
WOOD_LIGHT = (86, 63, 52, 255)
IRON = (116, 116, 126, 255)
IRON_LIGHT = (162, 162, 174, 255)
IRON_DARK = (72, 72, 82, 255)


def noise(x, y):
    value = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return (value >> 16) & 7


def stone_field(canvas):
    for y in range(SIZE):
        for x in range(SIZE):
            shade = noise(x, y)
            if shade == 0:
                canvas.put(x, y, STONE_DARK)
            elif shade >= 6:
                canvas.put(x, y, STONE_LIGHT)
            else:
                canvas.put(x, y, STONE)


def stone_tiles(canvas):
    stone_field(canvas)
    for x in range(SIZE):
        canvas.put(x, 7, STONE_EDGE)
    for y in range(0, 7):
        canvas.put(5, y, STONE_EDGE)
        canvas.put(12, y, STONE_EDGE)
    for y in range(8, SIZE):
        canvas.put(2, y, STONE_EDGE)
        canvas.put(9, y, STONE_EDGE)
    canvas.outline(0, 0, 15, 15, STONE_EDGE)


def gold_corners(canvas):
    for x0, y0, dx, dy in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        canvas.put(x0, y0, GOLD_LIGHT)
        canvas.put(x0 + dx, y0, GOLD)
        canvas.put(x0 + dx * 2, y0, GOLD)
        canvas.put(x0, y0 + dy, GOLD)
        canvas.put(x0, y0 + dy * 2, GOLD)


def gem(canvas, cx, cy):
    for y in range(SIZE):
        for x in range(SIZE):
            distance = abs(x - cx) + abs(y - cy)
            if distance <= 1.5:
                canvas.put(x, y, SPARK)
            elif distance <= 2.5:
                canvas.put(x, y, PURPLE_LIGHT)
            elif distance <= 3.5:
                canvas.put(x, y, PURPLE)
            elif distance <= 4.5:
                canvas.put(x, y, PURPLE_DEEP)


def core_side():
    canvas = Canvas(SIZE)
    stone_tiles(canvas)
    gold_corners(canvas)
    gem(canvas, 7.5, 7.5)
    return canvas


def core_top():
    canvas = Canvas(SIZE)
    stone_field(canvas)
    canvas.outline(0, 0, 15, 15, STONE_EDGE)
    canvas.outline(2, 2, 13, 13, GOLD)
    for y in range(SIZE):
        for x in range(SIZE):
            distance = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if 9.0 < distance <= 20.0:
                canvas.put(x, y, PURPLE_DEEP)
            elif 3.0 < distance <= 9.0:
                canvas.put(x, y, PURPLE)
            elif distance <= 3.0:
                canvas.put(x, y, SPARK)
    for offset in range(4, 7):
        canvas.put(7, 7 - offset + 3, PURPLE_LIGHT)
    canvas.rect(7, 2, 8, 3, PURPLE_LIGHT)
    canvas.rect(7, 12, 8, 13, PURPLE_LIGHT)
    canvas.rect(2, 7, 3, 8, PURPLE_LIGHT)
    canvas.rect(12, 7, 13, 8, PURPLE_LIGHT)
    return canvas


def core_bottom():
    canvas = Canvas(SIZE)
    stone_tiles(canvas)
    return canvas


def planks(canvas, seams):
    for y in range(SIZE):
        for x in range(SIZE):
            shade = noise(x, y)
            if shade == 0:
                canvas.put(x, y, WOOD_DARK)
            elif shade >= 6:
                canvas.put(x, y, WOOD_LIGHT)
            else:
                canvas.put(x, y, WOOD)
    for y in seams:
        for x in range(SIZE):
            canvas.put(x, y, WOOD_DARK)
    canvas.outline(0, 0, 15, 15, WOOD_DARK)


def iron_band(canvas, x0, y0, x1, y1):
    canvas.rect(x0, y0, x1, y1, IRON)
    if x1 - x0 >= y1 - y0:
        for x in range(x0, x1 + 1):
            canvas.put(x, y0, IRON_LIGHT)
            canvas.put(x, y1, IRON_DARK)
    else:
        for y in range(y0, y1 + 1):
            canvas.put(x0, y, IRON_LIGHT)
            canvas.put(x1, y, IRON_DARK)


def corner_brackets(canvas):
    for x0, y0, dx, dy in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        canvas.put(x0, y0, IRON_LIGHT)
        canvas.put(x0 + dx, y0, IRON)
        canvas.put(x0 + dx * 2, y0, IRON_DARK)
        canvas.put(x0, y0 + dy, IRON)
        canvas.put(x0, y0 + dy * 2, IRON_DARK)


def chest_side():
    canvas = Canvas(SIZE)
    planks(canvas, (4, 11))
    iron_band(canvas, 0, 0, 15, 1)
    iron_band(canvas, 0, 14, 15, 15)
    canvas.rect(5, 4, 10, 10, IRON)
    canvas.outline(5, 4, 10, 10, IRON_DARK)
    for x in range(6, 10):
        canvas.put(x, 5, IRON_LIGHT)
    canvas.rect(7, 6, 8, 6, PURPLE_LIGHT)
    canvas.rect(7, 7, 8, 7, PURPLE)
    canvas.rect(7, 8, 8, 8, PURPLE_DEEP)
    canvas.put(7, 9, PURPLE)
    canvas.put(8, 9, PURPLE_DEEP)
    return canvas


def chest_top():
    canvas = Canvas(SIZE)
    planks(canvas, (4, 8, 12))
    iron_band(canvas, 0, 0, 15, 1)
    iron_band(canvas, 0, 14, 15, 15)
    corner_brackets(canvas)
    canvas.rect(6, 6, 9, 9, IRON)
    canvas.outline(6, 6, 9, 9, IRON_DARK)
    canvas.rect(7, 7, 8, 8, PURPLE)
    canvas.put(7, 7, SPARK)
    canvas.put(8, 8, PURPLE_DEEP)
    return canvas


def chest_bottom():
    canvas = Canvas(SIZE)
    planks(canvas, (4, 8, 12))
    iron_band(canvas, 0, 0, 15, 1)
    iron_band(canvas, 0, 14, 15, 15)
    return canvas


def wood_panel(canvas, x0, y0, width, height):
    for y in range(y0, y0 + height):
        for x in range(x0, x0 + width):
            shade = noise(x, y)
            if shade == 0:
                canvas.put(x, y, WOOD_DARK)
            elif shade >= 6:
                canvas.put(x, y, WOOD_LIGHT)
            else:
                canvas.put(x, y, WOOD)
    for y in range(y0 + 3, y0 + height, 4):
        for x in range(x0, x0 + width):
            canvas.put(x, y, WOOD_DARK)


def metal_strip(canvas, x0, y0, width, height):
    canvas.rect(x0, y0, x0 + width - 1, y0 + height - 1, IRON)
    for y in range(y0, y0 + height):
        canvas.put(x0, y, IRON_LIGHT)
        canvas.put(x0 + width - 1, y, IRON_DARK)


def chest_entity():
    """Vanilla single-chest UV layout: lid at texOffs(0,0), body at texOffs(0,19), lock at texOffs(0,0)."""
    canvas = Canvas(64)
    lid_faces = ((0, 14, 14, 5), (14, 0, 14, 14), (28, 0, 14, 14),
                 (14, 14, 14, 5), (28, 14, 14, 5), (42, 14, 14, 5))
    body_faces = ((0, 33, 14, 10), (14, 19, 14, 14), (28, 19, 14, 14),
                  (14, 33, 14, 10), (28, 33, 14, 10), (42, 33, 14, 10))
    for x0, y0, width, height in lid_faces + body_faces:
        wood_panel(canvas, x0, y0, width, height)
    for x0, y0, width, height in lid_faces + body_faces:
        metal_strip(canvas, x0 + width // 2 - 1, y0, 2, height)
    for x0, y0, width, height in body_faces:
        metal_strip(canvas, x0, y0, width, 1)
        metal_strip(canvas, x0, y0 + height - 1, width, 1)

    canvas.rect(0, 0, 5, 4, IRON)
    canvas.rect(1, 1, 2, 4, IRON_LIGHT)
    canvas.put(1, 2, PURPLE)
    canvas.put(2, 2, PURPLE_LIGHT)
    canvas.put(1, 3, PURPLE_DEEP)
    canvas.put(2, 3, PURPLE)
    canvas.put(0, 1, IRON_DARK)
    canvas.put(3, 1, IRON_DARK)
    return canvas


def main():
    os.makedirs(ROOT, exist_ok=True)
    entity_root = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                               'assets', 'kingdoms', 'textures', 'entity', 'chest')
    os.makedirs(entity_root, exist_ok=True)
    chest_entity().save(os.path.join(entity_root, 'dungeon_chest.png'))
    print('wrote entity/chest/dungeon_chest.png')
    textures = {
        'dungeon_core_top': core_top(),
        'dungeon_core_side': core_side(),
        'dungeon_core_bottom': core_bottom(),
        'dungeon_chest_top': chest_top(),
        'dungeon_chest_side': chest_side(),
        'dungeon_chest_bottom': chest_bottom(),
    }
    for name, canvas in textures.items():
        canvas.save(os.path.join(ROOT, name + '.png'))
        print('wrote', name + '.png')


if __name__ == '__main__':
    main()
