import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
from icon import render

A = 'android/app/src/main'
pk = A + '/java/ir/flicker/flanner'
os.makedirs(pk, exist_ok=True)
shutil.copy('native/FlannerPlugin.java', pk + '/FlannerPlugin.java')
open(pk + '/MainActivity.java', 'w').write('''package ir.flicker.flanner;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FlannerPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
''')

mf = A + '/AndroidManifest.xml'
t = open(mf, encoding='utf8').read()
add = ('<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY"/>\n'
       '    <uses-permission android:name="android.permission.VIBRATE"/>\n'
       '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>\n'
       '    <uses-permission android:name="android.permission.USE_EXACT_ALARM"/>\n'
       '    <queries><intent><action android:name="android.intent.action.MAIN"/>'
       '<category android:name="android.intent.category.LAUNCHER"/></intent></queries>\n    ')
assert '<application' in t
t = t.replace('<application', add + '<application', 1)
open(mf, 'w', encoding='utf8').write(t)

res = A + '/res'
os.makedirs(res + '/drawable', exist_ok=True)
open(res + '/drawable/ic_stat_flanner.xml', 'w').write('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M12,2a10,10 0 1,0 0,20a10,10 0 1,0 0,-20zM12.5,7h-1.5v6l5.2,3.1 0.8,-1.2 -4.5,-2.7z"/></vector>')


def rounded(img, circle=False):
    m = Image.new('L', img.size, 0)
    r = img.size[0] // 2 if circle else int(img.size[0] * 0.22)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius=r, fill=255)
    o = img.copy(); o.putalpha(m); return o

# legacy (pre-Android 8) icons + adaptive layers (Android 8+, themed/monochrome on Android 13+)
for d, z in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    os.makedirs(f'{res}/mipmap-{d}', exist_ok=True)
    full = render(z, 'full')
    rounded(full).save(f'{res}/mipmap-{d}/ic_launcher.png')
    rounded(full, True).save(f'{res}/mipmap-{d}/ic_launcher_round.png')
for d, z in [('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)]:
    render(z, 'fg').save(f'{res}/mipmap-{d}/ic_launcher_foreground.png')
    render(z, 'mono').save(f'{res}/mipmap-{d}/ic_launcher_monochrome.png')
os.makedirs(res + '/mipmap-anydpi-v26', exist_ok=True)
xml = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
       '  <background android:drawable="@color/ic_launcher_background"/>\n'
       '  <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
       '  <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>\n</adaptive-icon>\n')
for n in ('ic_launcher.xml', 'ic_launcher_round.xml'):
    open(f'{res}/mipmap-anydpi-v26/{n}', 'w').write(xml)
os.makedirs(res + '/values', exist_ok=True)
open(res + '/values/ic_launcher_background.xml', 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#52628C</color>\n</resources>\n')
print('patched')
