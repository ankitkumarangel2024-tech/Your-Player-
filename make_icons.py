from PIL import Image, ImageDraw, ImageFilter
S = 1024

def background():
    img = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(img)
    for y in range(S):
        t = y / S
        d.line([(0, y), (S, y)], fill=(int(16 - 12 * t), int(34 - 28 * t), int(70 - 58 * t), 255))
    glow = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([-200, -250, 600, 450], fill=(40, 170, 255, 120))
    g.ellipse([450, 600, 1250, 1300], fill=(150, 90, 255, 110))
    return Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(120)))

def symbol(img, scale):
    s = S * scale
    cx, cy = S / 2, S / 2
    pts = [(cx - s * 0.36, cy - s * 0.5), (cx - s * 0.36, cy + s * 0.5), (cx + s * 0.64, cy)]
    w = int(s * 0.12)
    mask = Image.new('L', (S, S), 0)
    m = ImageDraw.Draw(mask)
    m.polygon(pts, fill=255)
    m.line(pts + [pts[0], pts[1]], fill=255, width=w, joint='curve')
    for p in pts:
        m.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=255)
    halo = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    halo.paste((80, 200, 255, 255), (0, 0), mask)
    img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(48)))
    grad = Image.new('RGBA', (S, S))
    gd = ImageDraw.Draw(grad)
    for x in range(S):
        t = x / S
        gd.line([(x, 0), (x, S)], fill=(int(110 + 65 * t), int(215 - 85 * t), 255, 255))
    img.paste(grad, (0, 0), mask)
    return img

full = symbol(background(), 0.30)
full.resize((512, 512), Image.LANCZOS).save('icon-maskable-512.png')
rounded = symbol(background(), 0.38)
cut = Image.new('L', (S, S), 0)
ImageDraw.Draw(cut).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
rounded.putalpha(cut)
rounded.resize((512, 512), Image.LANCZOS).save('icon-512.png')
rounded.resize((192, 192), Image.LANCZOS).save('icon-192.png')
print('icons ok')
