# UltPlug — MargyT Fake Likes

Plugin for MargyT that discovers numeric TextViews on resumed TikTok screens and logs their class, resource ID, position and size. This is the first diagnostic build used to identify the real Like Counter safely before replacing it.

## Build

The GitHub Actions workflow builds the `.mtp` using the MargyT plugin toolchain:

```text
python3 -m margyt.plugin examples/ultplug
```

The resulting `margyt.faklikes.mtp` is uploaded as the `margyt-fake-likes` Actions artifact.

## Current behavior

The plugin does not yet replace every numeric TextView. It records candidates in the MargyT diary so the actual TikTok view hierarchy can be identified without corrupting unrelated counters. Fake value defaults to `125000` and is stored in the plugin preferences under `fake_likes`.
