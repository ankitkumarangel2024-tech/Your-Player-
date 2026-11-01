import os
from PIL import Image
man = 'android/app/src/main/AndroidManifest.xml'
s = open(man, encoding='utf-8').read()
add = ''.join('    <uses-permission android:name="android.permission.%s" />\n' % p
              for p in ['WAKE_LOCK', 'READ_MEDIA_VIDEO'] if 'android.permission.%s"' % p not in s)
add += '    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />\n'
if add:
    s = s.replace('<application', add + '    <application', 1)
s = s.replace('<application', '<application android:name=".CrashApp" android:usesCleartextTraffic="true"', 1)
open(man, 'w', encoding='utf-8').write(s)
print('manifest ok')
g = 'android/app/build.gradle'
t = open(g, encoding='utf-8').read()
t = t.replace('dependencies {', 'dependencies {\n    implementation "androidx.media3:media3-exoplayer:1.3.1"\n    implementation "androidx.media3:media3-ui:1.3.1"', 1)
open(g, 'w', encoding='utf-8').write(t)
print('gradle ok')
res = 'android/app/src/main/res'
icon = Image.open('icon-512.png').convert('RGBA')
fore = Image.open('icon-maskable-512.png').convert('RGBA')
sizes = {'mdpi': (48, 108), 'hdpi': (72, 162), 'xhdpi': (96, 216), 'xxhdpi': (144, 324), 'xxxhdpi': (192, 432)}
for d, (legacy, adaptive) in sizes.items():
    folder = os.path.join(res, 'mipmap-' + d)
    if not os.path.isdir(folder):
        continue
    icon.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher.png'))
    icon.resize((legacy, legacy), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher_round.png'))
    fore.resize((adaptive, adaptive), Image.LANCZOS).save(os.path.join(folder, 'ic_launcher_foreground.png'))
bg = os.path.join(res, 'values', 'ic_launcher_background.xml')
if os.path.exists(bg):
    open(bg, 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                        '    <color name="ic_launcher_background">#04060C</color>\n</resources>\n')
print('icons patched')
