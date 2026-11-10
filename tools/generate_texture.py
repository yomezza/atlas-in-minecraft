"""Generate placeholder pixel art under build/, preserving the supplied texture. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw
import random

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / 'build/generated-placeholder/assets/atlas_companion'
(assets / 'textures/entity').mkdir(parents=True, exist_ok=True)
rng = random.Random(42)
image = Image.new('RGBA', (128, 128), (15, 16, 22, 255))
d = ImageDraw.Draw(image)
# Upper-left tile: armour. UV triangle (32,2), (2,62), (62,62).
for y in range(64):
    for x in range(64):
        noise = rng.randrange(0, 7)
        image.putpixel((x,y), (18+noise, 20+noise, 29+noise, 255))
d.polygon([(32,2),(2,62),(62,62)], fill=(25,27,37), outline=(94,51,45), width=2)
d.line([(32,8),(8,58),(56,58),(32,8)], fill=(138,43,37), width=1)
d.line([(32,17),(32,38)], fill=(255,65,34), width=2)
d.polygon([(32,23),(23,43),(41,43)], fill=(54,15,22), outline=(245,48,30), width=1)
d.polygon([(32,29),(28,39),(36,39)], fill=(255,151,73))
for y in (46,49,52):
    d.line((29,y,35,y), fill=(201,55,37), width=1)
for x in (15,47): d.rectangle((x,54,x+1,55), fill=(222,104,64))
# Upper-right tile: emissive core. Same triangle, with x+64.
for y in range(64):
    for x in range(64,128):
        distance = abs(x-96) + abs(y-35)*0.6
        image.putpixel((x,y), (255, max(18,int(123-distance*2)), max(8,int(52-distance)), 255))
d.line([(96,6),(70,60),(122,60),(96,6)], fill=(255,180,91), width=2)
d.polygon([(96,21),(85,46),(107,46)], outline=(255,229,173), width=2)
d.line((96,14,96,53), fill=(255,210,131), width=1)
# Lower half reserved for a future model; a material strip is supplied for artists.
for x1,c in [(0,(22,24,33)),(32,(88,46,42)),(64,(247,43,24)),(96,(255,182,84))]:
    d.rectangle((x1,64,x1+31,127), fill=(*c,255))
image.save(assets / 'textures/entity/atlas.png')

icon = Image.new('RGBA',(128,128),(12,14,23,255)); p=ImageDraw.Draw(icon)
for inset in range(12,0,-1):
    p.ellipse((16-inset,16-inset,112+inset,112+inset),outline=(30+inset*2,12,19,255))
p.polygon([(64,10),(108,62),(64,117),(20,62)],fill=(50,21,28),outline=(237,60,38),width=3)
p.polygon([(64,20),(95,62),(64,62)],fill=(40,42,54))
p.polygon([(64,20),(33,62),(64,62)],fill=(21,24,34))
p.polygon([(33,66),(64,105),(64,66)],fill=(31,32,43))
p.polygon([(95,66),(64,105),(64,66)],fill=(53,34,38))
p.polygon([(64,41),(83,64),(64,88),(45,64)],fill=(255,62,31),outline=(255,173,91),width=2)
p.polygon([(64,52),(73,64),(64,77),(55,64)],fill=(255,210,139))
icon.save(assets / 'icon.png')

if __name__ == '__main__': print('Created placeholder texture and icon in build/generated-placeholder/.')
